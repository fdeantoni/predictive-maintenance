package com.example.machine

import java.time.Instant

import org.scalatest.{Matchers, WordSpec}
import breeze.stats._

//noinspection TypeAnnotation
class CalculatorTest extends WordSpec with Matchers {

  class TestRecord(instant: Instant, value: Double) {
    val record = Record(
      machine = "TestMachine",
      instant = instant,
      tick = value.toLong,
      volt = value,
      pressure = value,
      rotate = value,
      vibration = value,
      error1 = false,
      error2 = false,
      error3 = false,
      error4 = false,
      service1 = false,
      service2 = false,
      service3 = false,
      service4 = false,
      prediction = None
    )
    val json = record.toJson
  }

  //noinspection TypeAnnotation
  trait Events36 {
    val start = Instant.now()
    val records = for(i <- 1 to 36) yield {
      val datetime = start.plusSeconds(i)
      new TestRecord(datetime, i).record
    }
  }

  "Calculator" should {

    "calculate mean and std deviation" in new Events36 {
      val calculator = new Calculator(modelsDir = "target")
      val result = calculator.process("TestMachine", records)
      result.foreach(println)
      val sequence: Seq[Double] = (1 to 36).map(_.toDouble)
      val mean12: Double = mean(sequence.takeRight(12))
      val std12: Double = stddev(sequence.takeRight(12))
      val mean24: Double = mean(sequence.takeRight(24))
      val std24: Double = stddev(sequence.takeRight(24))
      val mean36: Double = mean(sequence)
      val std36: Double = stddev(sequence)
      println(s"mean12: $mean12, mean24: $mean24, mean36: $mean36, stddev12: $std12, stddev24: $std24, stddev36: $std36")
      result.get._2.pressure.mean12 should equal (mean12)
      result.get._2.pressure.mean24 should equal (mean24)
      result.get._2.pressure.mean36 should equal (mean36)
      result.get._2.pressure.stddev12 should equal (std12)
      result.get._2.pressure.stddev24 should equal (std24)
      result.get._2.pressure.stddev36 should equal (std36)
    }
  }

}
