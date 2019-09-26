package com.example.machine

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.{KillSwitch, KillSwitches}
import akka.stream.scaladsl._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.duration._
import scala.concurrent.Future

//noinspection TypeAnnotation
class KafkaProducer(brokers: String, system: ActorSystem) {

  val kafkaTopic = "input"

  private def settings: ProducerSettings[String, String] = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(brokers)

  val sink: Sink[ProducerRecord[String, String], _] = {
    RestartSink.withBackoff(
      minBackoff = 3.seconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
    ) { () =>
      Producer.plainSink(settings)
    }
  }

  val killSwitch = KillSwitches.single[ProducerRecord[String, String]]

  val flow: Flow[Record, ProducerRecord[String, String], KillSwitch] = Flow[Record]
    .log("stream-kafka")
    .mapAsync(1) { value =>
      Future.successful(new ProducerRecord[String, String](kafkaTopic, value.machine, value.toJson))
    }.viaMat(KillSwitches.single)(Keep.right)

}
