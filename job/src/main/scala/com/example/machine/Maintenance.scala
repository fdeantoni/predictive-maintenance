package com.example.machine

import org.apache.spark._
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.rogach.scallop.ScallopConf

object Maintenance {

  def main(args: Array[String]): Unit = {

    // Load the command line switches first
    val conf = new Conf(args)

    // Recreate old stream (if checkpoint folder exists) or create a new one
    val ssc = StreamingContext.getOrCreate(conf.checkpoint(), () => {
      createContext(conf.checkpoint())
    })

    val stream = new KafkaReceiver(conf.brokers(), conf.input()).stream(ssc)

    val calculator = new Calculator(conf.models())

    val result = calculator.calculate(stream)

    //KafkaWriter.write(result, conf.brokers(), conf.output())
    result.print()

    // Start the stream and await termination
    ssc.start()
    ssc.awaitTermination()
  }

  private def createContext(checkpoint: String): StreamingContext = {
    val config = new SparkConf().setAppName("Predictive Maintenance")
    val ssc = new StreamingContext(config, Seconds(4))
    ssc.checkpoint(checkpoint)

    ssc
  }

  //noinspection TypeAnnotation
  class Conf(args: Seq[String]) extends ScallopConf(args) {
    printedName = "maintenance"
    banner(
      """
        |Usage: job-assembly-0.1.jar --brokers localhost:9092
        |By default the app will look for models in the ./models folder. This can
        |be overridden, see options:
      """.stripMargin)
    val checkpoint = opt[String](default = Some("./checkpoint"), descr = "Location for Spark checkpoint directory, default './checkpoint'.")
    val models = opt[String](default = Some("./models"), descr = "Directory containing all machine models, default './models'.")
    val brokers = opt[String](default = Some("localhost:9092"), descr = "Kafka broker, default localhost:9092")
    val input = opt[String](default = Some("input"), descr = "Topic containing input records")
    val output = opt[String](default = Some("output"), descr = "Topic for output records")
    verify()
  }

}
