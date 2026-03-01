import IndexDslGeneratorPlugin.autoImport._

scalaVersion := "2.13.18"

name := "yoctodb-query"

val schemaV = "1.8.1"
val AmmoniteVersion = "3.0.8"

lazy val javaVersion = sys.props("java.specification.version")

Compile / scalacOptions ++= Seq(
  "-Xsource:3-cross",
  s"-target:$javaVersion",
  s"-release:$javaVersion",
  "-Ylog-classpath",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xcheckinit",
  "-Ywarn-value-discard",
  "-Xlint",
  "-Wconf:cat=other-match-analysis:error",
  "-Wconf:msg=lambda-parens:s",
  "-Xmigration",
  "-Xfatal-warnings",
)

scalafmtOnCompile := true

libraryDependencies ++=
  Seq(
    "com.yandex.yoctodb" % "yoctodb-core" % "0.0.20",
    "ch.qos.logback"     %  "logback-classic" % "1.5.32",
    "org.scalameta"      %% "scalameta" % "4.15.2",

    "dev.zio" %% "zio-schema" % schemaV,
    "dev.zio" %% "zio-schema-derivation" % schemaV,
    "dev.zio" %% "zio-schema-json" % schemaV,

    "com.lihaoyi" % "ammonite" % AmmoniteVersion % "test" cross CrossVersion.full
  )

Compile / sourceGenerators += genIndexDsl

javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion)
javaHome := Some(file(s"/Library/Java/JavaVirtualMachines/jdk-$javaVersion.jdk/Contents/Home/"))

enablePlugins(IndexDslGeneratorPlugin)

//test:run
Test / sourceGenerators += Def.task {
  val file = (Test / sourceManaged).value / "amm.scala"
  IO.write(file, """object amm extends App { ammonite.Main().run() }""")
  Seq(file)
}.taskValue


addCommandAlias("c", "compile")
addCommandAlias("r", "reload")

//shellPrompt := { state => s"${javaVersion}> " }

//++2.13.18
//show javacOptions
//show scalacOptions
