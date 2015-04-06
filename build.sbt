import complete.DefaultParsers._

lazy val runParser = inputKey[Unit]("Runs the SQL interface with the given configuration.")
lazy val runPlanner = inputKey[Unit]("Runs the imperative interface with the given configuration.")

lazy val commonSettings = Seq(
  name := "squall",
  organization := "ch.epfl.data",
  version := "0.2.0",
  scalaVersion := "2.11.5"
)

lazy val squall = (project in file("squall-core")).
  // TODO: add java options
  settings(commonSettings: _*).
  settings(
    javacOptions ++= Seq(
      "-target", "1.7",
      "-source", "1.7"),
    unmanagedBase := baseDirectory.value / "../contrib",
    // We need to add Clojars as a resolver, as Storm depends on some
    // libraries from there.
    resolvers += "clojars" at "https://clojars.org/repo",
    libraryDependencies ++= Seq(
      // Versions that were changed when migrating from Lein to sbt are
      // commented just before the library
      "net.sf.jsqlparser" % "jsqlparser" % "0.7.0",
      "net.sf.trove4j" % "trove4j" % "3.0.2",
      "net.sf.opencsv" % "opencsv" % "2.3",
      // bdb-je: 5.0.84 -> 5.0.73
      "com.sleepycat" % "je" % "5.0.73",
      // storm-core: 0.9.2-incubating -> 0.9.3
      "org.apache.storm" % "storm-core" % "0.9.3",
      // clojure: 1.5.1 -> ?
      // [This one doesn't seem to be required]
      //"org.clojure" % "clojure" % "1.5.1"
      "junit" % "junit" % "4.12" % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test,
      "org.apache.hadoop" % "hadoop-client" % "2.2.0" exclude("org.slf4j", "slf4j-log4j12"),
      "org.apache.hadoop" % "hadoop-hdfs" % "2.2.0" exclude("org.slf4j", "slf4j-log4j12")
    ),
    // http://www.scala-sbt.org/0.13/docs/Running-Project-Code.html
    // We need to fork the JVM, as storm uses multiple threads
    fork := true,
    runParser := {
      val arguments: Seq[String] = spaceDelimited("<arg>").parsed
      val classpath: Seq[File] = (
        ((fullClasspath in Runtime).value map { _.data }) ++
          (arguments.tail map { file(_) })
      )
      val options = ForkOptions(
        bootJars = classpath,
        workingDirectory = Some(file("./bin"))
      )
      val mainClass: String = "ch.epfl.data.squall.api.sql.main.ParserMain"
      val exitCode: Int = Fork.java(options, mainClass +: arguments)
      sys.exit(exitCode)
    },
    runPlanner := {
      val arguments: Seq[String] = spaceDelimited("<arg>").parsed
      val classpath: Seq[File] = (
        ((fullClasspath in Runtime).value map { _.data }) ++
          (arguments.tail map { file(_) })
      )
      val options = ForkOptions(
        bootJars = classpath,
        workingDirectory = Some(file("./bin"))
      )
      val mainClass: String = "ch.epfl.data.squall.main.Main"
      val exitCode: Int = Fork.java(options, mainClass +: arguments)
      sys.exit(exitCode)
    },
    // Testing
    libraryDependencies +=  "org.scalatest" % "scalatest_2.11" % "2.2.4" % Test
  )

// For the macros
lazy val functional_macros = (project in file("squall-functional-macros")).
  dependsOn(squall).
  settings(commonSettings: _*).
  settings(
    name := "squall-frontend-core",
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _)
  )

lazy val functional = (project in file("squall-functional")).
  dependsOn(squall, functional_macros).
  settings(commonSettings: _*).
  settings(
//    fork := true,
    // TODO: this is only necessary because we are using the .jar for testing
    (test in Test) := {
      (Keys.`package` in Compile).value
        (test in Test).value
    },
    name := "squall-frontend",
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
    libraryDependencies +=  "org.scalatest" % "scalatest_2.11" % "2.2.4" % Test
  )

