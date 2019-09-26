package com.example.machine

import java.nio.file.Paths
import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._

class CsvConsumer(file: String, machine: String, system: ActorSystem) {

  val source: Source[ByteString, Future[IOResult]] = FileIO.fromPath(Paths.get(file))

  val flow: Flow[ByteString, Record, NotUsed] = Flow[ByteString]
    .via(CsvParsing.lineScanner())
    .via(CsvToMap.toMap())
    .throttle(1, 5.seconds)
    .map { line =>
      val tick = line.get("time").map(_.utf8String).map(_.toLong).getOrElse(0L)
      val volt = line.get("volt").map(_.utf8String).map(_.toDouble).getOrElse(0D)
      val rotate = line.get("rotate").map(_.utf8String).map(_.toDouble).getOrElse(0D)
      val pressure = line.get("pressure").map(_.utf8String).map(_.toDouble).getOrElse(0D)
      val vibration = line.get("vibration").map(_.utf8String).map(_.toDouble).getOrElse(0D)
      val error1 = line.get("error1").map(_.utf8String.toInt).contains(1)
      val error2 = line.get("error2").map(_.utf8String.toInt).contains(1)
      val error3 = line.get("error3").map(_.utf8String.toInt).contains(1)
      val error4 = line.get("error4").map(_.utf8String.toInt).contains(1)
      val service1 = line.get("service1").map(_.utf8String.toInt).contains(1)
      val service2 = line.get("service2").map(_.utf8String.toInt).contains(1)
      val service3 = line.get("service3").map(_.utf8String.toInt).contains(1)
      val service4 = line.get("service4").map(_.utf8String.toInt).contains(1)
      Record(
        machine = machine,
        instant = Instant.now(),
        tick = tick,
        volt = volt,
        pressure = pressure,
        rotate = rotate,
        vibration = vibration,
        error1 = error1,
        error2 = error2,
        error3 = error3,
        error4 = error4,
        service1 = service1,
        service2 = service2,
        service3 = service3,
        service4 = service4,
        prediction = None
      )
    }.log("stream-csv")



}
