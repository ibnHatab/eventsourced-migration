package org.eligosource.apm.example

import scala.concurrent._
import scala.concurrent.duration._

import akka.actor._
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout

object OrderExample extends App {
  implicit val system = ActorSystem("example")
  implicit val timeout = Timeout(5 seconds)

  import system.dispatcher

  val processor = system.actorOf(Props(classOf[OrderProcessor],
    system.actorOf(Props[CreditCardValidator]),
    system.actorOf(Props[Destination])), "orderProcessor")

  processor ? Persistent(OrderSubmitted(Order(details = "jelly beans", creditCardNumber = "1234-5678-1234-5678"))) onSuccess {
    case order: Order => println("received response %s" format order)
  }

  Thread.sleep(1000)
  system.shutdown()

  // ------------------------------------
  // domain object
  // ------------------------------------

  case class Order(id: Int = -1, details: String, validated: Boolean = false, creditCardNumber: String)

  // ------------------------------------
  // domain events
  // ------------------------------------

  case class OrderSubmitted(order: Order)
  case class OrderAccepted(order: Order)

  case class CreditCardValidationRequested(order: Order, processor: ActorRef)
  case class CreditCardValidated(orderId: Int)

  // ------------------------------------
  // event-sourced order processor
  // ------------------------------------

  class OrderProcessor(validator: ActorRef, destination: ActorRef) extends Processor {
    val validationRequestChannel = context.actorOf(Channel.props(), "validationRequests")
    val acceptedOrdersChannel = context.actorOf(Channel.props(), "acceptedOrders")

    var orders = Map.empty[Int, Order] // processor state

    def receive = {
      case p @ Persistent(OrderSubmitted(order), _) => {
        val id = orders.size
        val upd = order.copy(id = id)
        orders = orders + (id -> upd)
        validationRequestChannel forward Deliver(p.withPayload(CreditCardValidationRequested(upd, self)), validator)
      }
      case p @ Persistent(CreditCardValidated(orderId), _) => {
        orders.get(orderId).foreach { order =>
          val upd = order.copy(validated = true)
          orders = orders + (orderId -> upd)
          if (!recoveryRunning) sender ! upd
          acceptedOrdersChannel ! Deliver(p.withPayload(OrderAccepted(upd)), destination)
        }
        p.confirm()
      }
    }
  }

  // ------------------------------------
  //  channel destinations
  // ------------------------------------

  class CreditCardValidator extends Actor {
    def receive = {
      case p @ Persistent(CreditCardValidationRequested(order, processor), _) => {
        val sdr = sender  // initial sender
        Future {
          // do some credit card validation asynchronously
          // ...

          // and send back a successful validation result (preserving the initial sender)
          processor tell (p.withPayload(CreditCardValidated(order.id)), sdr)

          // please note that this receiver does NOT confirm message receipt. The confirmation
          // is done by the order processor when it receives the CreditCardValidated event.
        }
      }
    }
  }

  class Destination extends Actor {
    def receive = {
      case p @ Persistent(event, _) => {
        println("received event %s" format event)
        p.confirm()
      }
    }
  }
}
