package com.mooneyserver.akkapubsub

import akka.actor.{Props, ActorLogging, Actor}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.sharding.ShardRegion

// Domain
final case class PublisherUpdate(publisher: UpdateableEvent)

object PublisherActor {

  lazy val props = Props(classOf[PublisherActor])

  val idExtractor: ShardRegion.ExtractEntityId = {
    case s: PublisherUpdate => (s.publisher.id.toString, s.publisher)
  }

  val shardExtractor: ShardRegion.ExtractShardId = {
    case msg: PublisherUpdate => (msg.publisher.id % 100).toString
  }
}

class PublisherActor extends Actor with ActorLogging {

  log.info(s"Publisher ${self.path.name} actor instance created")

  val mediator = DistributedPubSub(context.system).mediator

  override def receive: Receive = {
    case event: UpdateableEvent => {
      log.info(s"$event to be routed to all listeners")
      mediator ! Publish(s"Publisher-${self.path.name}", event)
    }
  }
}
