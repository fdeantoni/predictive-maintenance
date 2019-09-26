package com.example.machine

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.spark.streaming.dstream.DStream

object KafkaWriter {

  def write(stream: DStream[Record], brokers: String = "localhost:9092", topic: String = "output"): Unit = {
    stream.foreachRDD { rdd =>
      rdd.foreachPartition { partition =>
        val producer = KafkaProducerFactory.getOrCreate(brokers)
        partition.foreach { output =>
          val record = new ProducerRecord[String, String](topic, output.machine, output.toJson)
          producer.send(record)
        }
      }
    }
  }

}
