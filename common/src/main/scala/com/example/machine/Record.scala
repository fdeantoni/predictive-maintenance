package com.example.machine

import java.time.Instant

import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

case class Record(
                   machine: String,
                   instant: Instant,
                   tick: Long,
                   volt: Double,
                   pressure: Double,
                   rotate: Double,
                   vibration: Double,
                   error1: Boolean,
                   error2: Boolean,
                   error3: Boolean,
                   error4: Boolean,
                   service1: Boolean,
                   service2: Boolean,
                   service3: Boolean,
                   service4: Boolean,
                   prediction: Option[Double]
                 ) {
  def toJson: String = Record.toJson(this)
}

object Record {
  implicit val formats = Serialization.formats(NoTypeHints) + new CustomSerializer[Instant](format => (
    {
      case JString(i) => Instant.parse(i)
      case JNull => null
    },
    {
      case i: Instant => JString(i.toString)
    }
  ))
  def fromJson(json: String): Record = {
    read[Record](json)
  }
  def toJson(record: Record): String = write(record)
}
