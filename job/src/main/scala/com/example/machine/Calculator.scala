package com.example.machine

import java.io.{File, FilenameFilter}
import java.time.Instant

import breeze.stats._
import ml.dmlc.xgboost4j.scala.{Booster, DMatrix, XGBoost}
import org.apache.spark.internal.Logging
import org.apache.spark.streaming._
import org.apache.spark.streaming.dstream.DStream

//noinspection DuplicatedCode
class Calculator(modelsDir: String) extends Logging with Serializable {

  /**
   * Load all the boosters from the modelsDir directory. Booster models must have a suffix of .xgboost.model,
   * and start with the Sensor name. For example, the model for sensor /Acme/Widget/Machine746 must be called
   * Machine746.xgboost.model.
   */
  val boosters: Map[String, Booster] = {
    val dir = new File(modelsDir)
    if(!dir.exists()) {
      log.warn(s"Directory $modelsDir does not exist! Will create it now but Spark Job will not generate any predictions.")
      dir.mkdirs()
    }
    val modelSuffix = ".xgboost.model"
    val files = dir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(modelSuffix)
    })
    files.toSeq.map(file => file.getName.stripSuffix(modelSuffix) -> XGBoost.loadModel(file.getCanonicalPath)).toMap
  }

  log.info(s"Loaded models from $modelsDir:\n  " + { if(boosters.isEmpty) "No models found!" else boosters.keys.mkString("  \n") })

  /**
   * Process a list of SensorEvents (belonging to one sensor entity). Processing involves calculating the rolling mean
   * and standard deviation based on the list of sensor events received.
   */
  private[machine] def process(key: String, records: Iterable[Record]): Option[(String, Calculator.Result)] = {
    val grouped = records.map(r => r.instant.toEpochMilli -> r).toMap
    if(log.isDebugEnabled()) log.debug("Window size: " + grouped.size)
    if(grouped.size >= 36) {
      val volt = records.map(r => r.instant -> r.volt)
      val pressure = records.map(r => r.instant -> r.pressure)
      val rotate = records.map(r => r.instant -> r.rotate)
      val vibration = records.map(r => r.instant -> r.vibration)
      val record = grouped.toVector.maxBy(_._1)._2
      Some(record.machine -> Calculator.Result(record, processField(volt), processField(pressure), processField(rotate), processField(vibration)))
    } else None
  }

  /**
   * Calculate the 12, 24, and 36 rolling mean and standard deviation
   */
  private def processField(values: Iterable[(Instant, Double)]): Calculator.Value = {
    if(values.isEmpty) {
      Calculator.Value()
    } else {
      val sorted = values.toSeq.sortBy(_._1.toEpochMilli)
      val s12 = sorted.takeRight(12).map(_._2)
      val s24 = sorted.takeRight(24).map(_._2)
      val s36 = sorted.takeRight(36).map(_._2)
      val mean12 = mean(s12)
      val mean24 = mean(s24)
      val mean36 = mean(s36)
      val std12 = stddev(s12)
      val std24 = stddev(s24)
      val std36 = stddev(s36)
      Calculator.Value(sorted.last._2, mean12, mean24, mean36, std12, std24, std36)
    }
  }

  /**
   * A Spark stateful calculation function that will calculate for us the "time since last" event on the error and
   * service fields in the SensorEvent. If the value contained in the field is 0 then we increment by 1. If the value
   * is not 0, then the event happened, and we start again with 1.
   */
  private def stateFunc = (key: String, value: Option[Calculator.Result], state: State[Calculator.Result]) => {
    (value, state.getOption()) match {
      case (Some(current), Some(previous)) if current.record.instant.isAfter(previous.record.instant) =>
        val error1 = if(!current.record.error1) previous.error1 + 1 else 1
        val error2 = if(!current.record.error2) previous.error2 + 1 else 1
        val error3 = if(!current.record.error3) previous.error3 + 1 else 1
        val error4 = if(!current.record.error4) previous.error4 + 1 else 1
        val service1 = if(!current.record.service1) previous.service1 + 1 else 1
        val service2 = if(!current.record.service2) previous.service2 + 1 else 1
        val service3 = if(!current.record.service3) previous.service3 + 1 else 1
        val service4 = if(!current.record.service4) previous.service4 + 1 else 1
        val updated = current.copy(error1 = error1, error2 = error2, error3 = error3, error4 = error4, service1 = service1, service2 = service2, service3 = service3, service4 = service4)
        state.update(updated)
        Some(updated)
      case (Some(current), None) =>
        val tick = current.record.tick
        val updated = current.copy(error1 = tick, error2 = tick, error3 = tick, error4 = tick, service1 = tick, service2 = tick, service3 = tick, service4 = tick)
        state.update(updated)
        Some(updated)
      case _ => None
    }
  }

  /**
   * Entry point for the calculator
   */
  def calculate(stream: DStream[(String, String)]): DStream[Record] = {

    /*
     *  First we group by key (i.e. the path) using a sliding window of 200 seconds, which will slide each 4 seconds. We do 4 seconds so that
     *  it is slightly faster than the data rate of the machine sensors (set at 5 seconds). This to ensure we always get all the events sent
     *  by the sensor.
     *
     *  Once grouped, we process the data to calculate the mean and standard deviations for volt, pressure, rotation, and vibration.
     *
     *  Next we map the events again based on the key (i.e. path) and then use the mapWithState function to calculate the "last time since" values
     *  for the error and service fields.
     *
     *  The output from these calculations are then converted to Kafka value messages, which can be fed into the KafkaDStreamSink.
     */
    stream.map { case (key, value) =>
        key -> Record.fromJson(value)
      }
      .groupByKeyAndWindow(Seconds(200), Seconds(4))
      .flatMap { case (key, records) =>
        process(key, records)
      }.mapWithState(StateSpec.function(stateFunc)).flatMap {
      case Some(result) => Some(result)
      case _ => None
    }.map { result =>
      resultToOutput(result)
    }
  }

  def resultToOutput(result: Calculator.Result): Record = {
    boosters.get(result.record.machine) match {
      case Some(booster) =>
        if(log.isDebugEnabled()) log.debug(s"Will perform prediction on:\n$result")
        val data = result.xgBoostData
        val output = booster.predict(data = data)
        if(log.isDebugEnabled()) log.debug(s"Prediction: ${result.record.machine}[${result.record.instant}] -> ${output.map(_.toSeq).toSeq}")
        val prediction = output.flatMap(_.toSeq).last
        result.record.copy(prediction = Some(prediction))
      case None =>
        if(log.isDebugEnabled()) log.debug(s"Model for ${result.record.machine} not found in $modelsDir.")
        result.record
    }
  }


}

object Calculator {

  implicit class RoundDouble(value: Double) {
    def round(precision: Int): Double = BigDecimal(value).setScale(precision, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  /**
   * Holds the calculated mean and standard deviation values for volt, pressure, rotation, and vibration
   */
  case class Value(original: Double = Double.NaN, mean12: Double = Double.NaN, mean24: Double = Double.NaN, mean36: Double = Double.NaN, stddev12: Double = Double.NaN, stddev24: Double = Double.NaN, stddev36: Double = Double.NaN) {
    override def toString: String = {
      s"original[$original] mean12[${mean12.round(2)}] mean24[${mean24.round(2)}] mean36[${mean36.round(2)}] stddev12[${stddev12.round(2)}] stddev24[${stddev24.round(2)}] stddev36[${stddev36.round(2)}]"
    }
  }

  /**
   * Holds the calculated result and returns it as a feature set the XGBoost model requires
   */
  case class Result(record: Record, volt: Value, pressure: Value, rotation: Value, vibration: Value, error1: Long = 0, error2: Long = 0, error3: Long = 0, error4: Long = 0, service1: Long = 0, service2: Long = 0, service3: Long = 0, service4: Long = 0) {

    /*
    Sequence of columns based on trained model:
      [1] "volt"             "rotate"           "pressure"         "vibration"        "volt_mean12"      "rotate_mean12"
      [7] "pressure_mean12"  "vibration_mean12" "volt_std12"       "rotate_std12"     "pressure_std12"   "vibration_std12"
     [13] "volt_mean24"      "rotate_mean24"    "pressure_mean24"  "vibration_mean24" "volt_std24"       "rotate_std24"
     [19] "pressure_std24"   "vibration_std24"  "volt_mean36"      "rotate_mean36"    "pressure_mean36"  "vibration_mean36"
     [25] "volt_std36"       "rotate_std36"     "pressure_std36"   "vibration_std36"  "service1"         "service2"
     [31] "service3"         "service4"         "error1"           "error2"           "error3"           "error4"
     */
    def xgBoostData: DMatrix = {
      val data: Array[Float] = Array(
        volt.original,
        rotation.original,
        pressure.original,
        vibration.original,
        volt.mean12,
        rotation.mean12,
        pressure.mean12,
        vibration.mean12,
        volt.stddev12,
        rotation.stddev12,
        pressure.stddev12,
        vibration.stddev12,
        volt.mean24,
        rotation.mean24,
        pressure.mean24,
        vibration.mean24,
        volt.stddev24,
        rotation.stddev24,
        pressure.stddev24,
        vibration.stddev24,
        volt.mean36,
        rotation.mean36,
        pressure.mean36,
        vibration.mean36,
        volt.stddev36,
        rotation.stddev36,
        pressure.stddev36,
        vibration.stddev36,
        service1,
        service2,
        service3,
        service4,
        error1,
        error2,
        error3,
        error4
      ).map(_.toFloat)
      new DMatrix(data, 1, 36)
    }

    override def toString: String = {
      s"""${record.machine}[${record.instant}]:
         |  volt:      $volt
         |  pressure:  $pressure
         |  rotation:  $rotation
         |  vibration: $vibration
         |  error:     $error1, $error2, $error3, $error4
         |  service:   $service1, $service2, $service3, $service4
       """.stripMargin
    }
  }

}

