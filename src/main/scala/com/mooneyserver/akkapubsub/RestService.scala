package com.mooneyserver.akkapubsub

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.server.Directives

// domain model
final case class Subscription(id: Long, publisherId: Long)
final case class UpdateableEvent(id: Long, value: Double)

// collect your json format instances into a support trait:
trait PubSubApiJsonProtocl extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val subscriptionFormat = jsonFormat2(Subscription)
  implicit val updateFormat = jsonFormat2(UpdateableEvent)
}

class RestService(subscriberShard: ActorRef, publisherShard: ActorRef)
  extends Directives with PubSubApiJsonProtocl {

  val routes: Route = path("subscriber") {
    post {
      entity(as[Subscription]) { subscription =>
        subscriberShard ! NewSubscriber(subscription)
        complete((StatusCodes.Accepted, "subscription created"))
      }
    }
  } ~ path("publisher") {
    post {
      entity(as[UpdateableEvent]) { event =>
        publisherShard ! PublisherUpdate(event)
        complete((StatusCodes.Accepted, "event updated"))
      }
    }
  } ~ path("health") {
    get {
      complete("All's good in the hood!")
    }
  }
}
