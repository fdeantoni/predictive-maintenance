package com.example.machine

import akka.Done
import akka.actor._
import akka.stream.ActorMaterializer
import com.salesforce.kafka.test.{KafkaTestCluster, KafkaTestUtils}
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._

//noinspection TypeAnnotation
object Simulator {

  implicit val system = ActorSystem("simulator")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher: ExecutionContext = system.dispatcher

  val kafka = new KafkaTestCluster(1)
  val kafkaUtils = new KafkaTestUtils(kafka)

  def main(args: Array[String]): Unit = {

    kafka.start()
    println("Kafka running at " + kafka.getKafkaConnectString)
    Thread.sleep(1000)
    kafkaUtils.createTopic("input", 1, 1)
    kafkaUtils.createTopic("output", 1, 1)
    println(kafkaUtils.describeClusterNodes().asScala)

    val machine = "Machine746"
    val csv = "../explore/build/machine_746.csv"
    val brokers = kafka.getKafkaConnectString

    val stream = new RecordStream(machine, csv, brokers)
    val result = stream.start

    result.map(_.count).onComplete { status =>
      println(s"Processed ${status.getOrElse(0)} lines.")
    }

    CoordinatedShutdown(system)
      .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "shutdown") { () =>
        kafka.stop()
        Future(Done)
      }
  }

}
