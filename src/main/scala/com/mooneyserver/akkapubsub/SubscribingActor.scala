package com.mooneyserver.akkapubsub

import akka.actor.{Props, ActorLogging, ReceiveTimeout}
import akka.actor.SupervisorStrategy.Stop
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.sharding.ShardRegion
import akka.persistence.PersistentActor
import akka.cluster.sharding.ShardRegion.Passivate
import akka.cluster.pubsub.DistributedPubSubMediator.{ Subscribe, SubscribeAck }

import scala.concurrent.duration._

// Domain Models
final case class NewSubscriber(bet: Subscription)
final case class SubscriberState(subscribedPublisher: Option[Long])

object SubscribingActor {
  lazy val props = Props(classOf[SubscribingActor])

  val idExtractor: ShardRegion.ExtractEntityId = {
    case b: NewSubscriber => (b.bet.id.toString, b.bet)
    case a:Any => println("Unexpected message in Bet Shard. No Route"); ("ERR", a)
  }

  val shardExtractor: ShardRegion.ExtractShardId = {
    case msg: NewSubscriber => (msg.bet.publisherId % 100).toString
  }
}

class SubscribingActor extends PersistentActor with ActorLogging {

  log.info(s"Subscriber-${self.path.name} actor instance created")

  context.setReceiveTimeout(20 seconds)

  var state: SubscriberState = SubscriberState(subscribedPublisher = None)

  val persistenceId: String = "Subscriber-" + self.path.name
  val mediator = DistributedPubSub(context.system).mediator

  def updateSubscriberState(publisher: UpdateableEvent): SubscriberState =
    state.copy(subscribedPublisher = Some(publisher.id))

  override def receiveRecover: Receive = {
    case event: UpdateableEvent => {
      log.info(s"Recovering $event for $persistenceId")
      state = updateSubscriberState(event)
    }
  }

  override def receiveCommand: Receive = {
    case event: UpdateableEvent => persist(event) { _ =>
      log.info(s"Applying $event to $persistenceId")
      state = updateSubscriberState(event)
    }
    case subscription: Subscription => {
      log.info(s"$persistenceId is subscribing to ${subscription.publisherId}")
      mediator ! Subscribe(s"Publisher-${subscription.publisherId}", self)
    }
    case SubscribeAck(Subscribe(subscribedTopic, group, subscribee)) ⇒
      log.info(s"$subscribee is subscribed to $subscribedTopic");
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
    case Stop => {
      log.info(s"$persistenceId is going to sleep now")
      context.stop(self)
    }
  }
}
