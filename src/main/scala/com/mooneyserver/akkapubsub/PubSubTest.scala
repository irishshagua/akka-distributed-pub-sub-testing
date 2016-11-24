package com.mooneyserver.akkapubsub

import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

object PubSubTest {

  implicit val system = ActorSystem("PubSubTestSystem")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  def main(args: Array[String]) {
    val betShard = ClusterSharding(system).start(
      typeName = "SubscriberShard",
      entityProps = SubscribingActor.props,
      settings = ClusterShardingSettings(system),
      extractEntityId = SubscribingActor.idExtractor,
      extractShardId = SubscribingActor.shardExtractor)

    val selectionShard = ClusterSharding(system).start(
      typeName = "PublisherShard",
      entityProps = PublisherActor.props,
      settings = ClusterShardingSettings(system),
      extractEntityId = PublisherActor.idExtractor,
      extractShardId = PublisherActor.shardExtractor)

    Http().bindAndHandle(
      new RestService(betShard, selectionShard).routes,
      "localhost",
      8080)
  }
}
