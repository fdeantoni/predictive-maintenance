
lazy val commonSettings = Seq(
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.12"
)

lazy val root = (project in file("."))
  .settings(name := "maintenance")
  .settings(commonSettings)
  .settings(
    publish := { },
    publishLocal := { }
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .aggregate(common, simulator, job)

lazy val common = (project in file("common"))
  .settings(name := "common")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % "3.5.3"
    )
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

lazy val simulator = (project in file("simulator"))
  .settings(name := "simulator")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.25",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.25",
      "com.typesafe.akka" %% "akka-stream" % "2.5.25",
      "com.typesafe.akka" %% "akka-stream-kafka" % "1.0.5",
      "com.lightbend.akka" %% "akka-stream-alpakka-csv" % "1.1.1",
      "com.salesforce.kafka.test" % "kafka-junit-core" % "3.1.1",
      "org.apache.kafka" %% "kafka" % "2.1.1",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
  )
  .settings(
    fork in run := true,
    cancelable in Global := true
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(common)


lazy val job = (project in file("job"))
  .settings(name := "job")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % "2.4.4" % "provided",
      "org.apache.spark" %% "spark-streaming" % "2.4.4" % "provided",
      "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.4.4",
      "org.scalanlp" %% "breeze" % "1.0",
      "org.scalanlp" %% "breeze-natives" % "1.0",
      "ml.dmlc" % "xgboost4j-spark" % "0.90",
      "org.rogach" %% "scallop" % "3.1.3",
      "ch.qos.logback" % "logback-classic" % "1.2.3" % "test",
      "org.scalatest" %% "scalatest" % "3.0.5" % "test"
    ).map(_.exclude("org.slf4j", "slf4j-log4j12"))
  )
  .settings(
    assemblyMergeStrategy in assembly := {
      case PathList("org","aopalliance", xs @ _*) => MergeStrategy.last
      case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
      case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
      case PathList("org", "apache", xs @ _*) => MergeStrategy.last
      case PathList("net", "jpountz", xs @ _*) => MergeStrategy.last
      case PathList("com", "esotericsoftware", "minlog", xs @ _*) => MergeStrategy.last
      case "plugin.properties"           => MergeStrategy.last
      case "log4j.properties"            => MergeStrategy.last
      case "git.properties"            => MergeStrategy.rename
      case "application.conf"            => MergeStrategy.concat
      case "reference.conf"              => MergeStrategy.concat
      case "overview.html"               => MergeStrategy.rename
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
  .dependsOn(common)

