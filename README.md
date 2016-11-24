# Test Akka Distributed Pub Sub Mechanism
The purpose of this test was to see how the [Distributed PubSub](http://doc.akka.io/docs/akka/current/scala/distributed-pub-sub.html)
feature in Akka reacted to subscribee actors getting passivated. The hope was that the passivated actors would be
rehydrated when a new publish event occured.

## Setup
To model the problem we need a publisher:

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

And a subscriber:

    class SubscribingActor extends PersistentActor with ActorLogging {

      var state: SubscriberState = SubscriberState(subscribedPublisher = None)
      val mediator = DistributedPubSub(context.system).mediator

      context.setReceiveTimeout(20 seconds)

      def updateSubscriberState(publisher: UpdateableEvent): SubscriberState =
        state.copy(subscribedPublisher = Some(publisher.id))


      override def receiveCommand: Receive = {
        case event: UpdateableEvent => persist(event) state = updateSubscriberState(event)
        case subscription: Subscription => mediator ! Subscribe(s"Publisher-${subscription.publisherId}", self)
        case SubscribeAck(Subscribe(subscribedTopic, group, subscribee)) ⇒ noop
        case ReceiveTimeout => context.stop(self)
      }
    }

These would be fronted by a very simple REST interface.


## Execution
Testing was quite simple. Using a curl request, create a Subscriber.


      curl -X POST -H "Content-Type: application/json" -d @subscription.json 127.0.0.1:8080/subscriber

Before the subscriber is passivated, publish an event and make sure that the subscriber received the event.

      curl -X POST -H "Content-Type: application/json" -d @updateableEvent.json 127.0.0.1:8080/publisher

And then after the subscriber was passivated, resubmit the event and see if the subscriber was awoken to process the
event.


## Results
Unfortunately as can be seen from the logs below, the mediator seems to only hold a reference to the current actor path
and therefore, the event which is published after the actor is passivated does not wake the sleeping subscriber from its
slumber... :disappointed:

      15:06:23.259 c.m.a.SubscribingActor - Subscriber-1234567 actor instance created
      15:06:23.262 c.m.a.SubscribingActor - Subscriber-1234567 is subscribing to 7654321
      15:06:23.268 c.m.a.SubscribingActor - Actor[akka://PubSubTestSystem/system/sharding/SubscriberShard/21/1234567#1516056761] is subscribed to Publisher-7654321
      15:06:31.085 c.m.a.PublisherActor - Publisher 7654321 actor instance created
      15:06:31.086 c.m.a.PublisherActor - UpdateableEvent(7654321,40.5) to be routed to all listeners
      15:06:31.090 c.m.a.SubscribingActor - Applying UpdateableEvent(7654321,40.5) to Subscriber-1234567
      15:06:51.107 c.m.a.SubscribingActor - Subscriber-1234567 is going to sleep now
      15:07:01.704 c.m.a.PublisherActor - UpdateableEvent(7654321,40.5) to be routed to all listeners
      15:07:01.704 a.a.LocalActorRef - Message [com.mooneyserver.akkapubsub.UpdateableEvent] from Actor[akka://PubSubTestSystem/system/sharding/PublisherShard/21/7654321#65243080] to Actor[akka://PubSubTestSystem/system/sharding/SubscriberShard/21/1234567#1516056761] was not delivered. [3] dead letters encountered. This logging can be turned off or adjusted with configuration settings 'akka.log-dead-letters' and 'akka.log-dead-letters-during-shutdown'