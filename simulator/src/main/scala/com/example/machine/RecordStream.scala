package com.example.machine

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, IOResult}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph}

import scala.concurrent.Future

class RecordStream(machine: String, csv: String, brokers: String)(implicit system: ActorSystem) {

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val consumer = new CsvConsumer(csv, machine, system)
  val kafka = new KafkaProducer(brokers, system)

  private val graph = RunnableGraph.fromGraph(GraphDSL.create(consumer.source) { implicit builder => input =>
    import GraphDSL.Implicits._

    input ~> consumer.flow ~> kafka.flow ~> kafka.killSwitch ~> kafka.sink

    ClosedShape
  })

  val start: Future[IOResult] = graph.run()

}
