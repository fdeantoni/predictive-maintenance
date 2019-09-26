package com.example.machine

import akka.Done
import akka.actor._
import akka.stream.ActorMaterializer
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

import scala.concurrent.{ExecutionContext, Future}

//noinspection TypeAnnotation
object Simulator {

  implicit val system = ActorSystem("simulator")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher: ExecutionContext = system.dispatcher

  implicit val kafkaonfig = EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 0)

  def main(args: Array[String]): Unit = {

    EmbeddedKafka.start()
    Thread.sleep(1000)
    EmbeddedKafka.createCustomTopic("input", Map.empty, 1, 1)
    EmbeddedKafka.createCustomTopic("output", Map.empty, 1, 1)

    val machine = "Machine746"
    val csv = "../explore/build/machine_746.csv"
    val brokers = "localhost:9092"

    val stream = new RecordStream(machine, csv, brokers)
    val result = stream.start

    result.map(_.count).onComplete { status =>
      println(s"Processed ${status.getOrElse(0)} lines.")
    }

    CoordinatedShutdown(system)
      .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "shutdown") { () =>
        EmbeddedKafka.stop()
        Future(Done)
      }
  }

}
