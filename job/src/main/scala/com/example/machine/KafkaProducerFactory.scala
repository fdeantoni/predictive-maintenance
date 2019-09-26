package com.example.machine

import java.util.Properties

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.spark.internal.Logging

object KafkaProducerFactory extends Logging {

  private var producer: Option[KafkaProducer[String, String]] = None

  def getOrCreate(brokers: String): KafkaProducer[String, String] = {
    producer.getOrElse {
      val p = create(brokers)
      sys.addShutdownHook {
        p.close()
      }
      producer = Some(p)
      p
    }
  }

  private def create(brokers: String) = {
    def properties = {
      val props = new Properties()
      props.put("bootstrap.servers", brokers)
      props.put("acks", "all")
      props.put("retries", "2")
      props.put("batch.size", "16384")
      props.put("linger.ms", "100")
      props.put("buffer.memory", "33554432")
      props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
      props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
      props
    }
    new KafkaProducer[String, String](properties)
  }
}
