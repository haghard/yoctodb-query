import IndexDslGeneratorPlugin.autoImport._

scalaVersion := "2.13.16"

name := "yoctodb-query"

val schemaV = "1.6.1"
val AmmoniteVersion = "3.0.2"

Compile / scalacOptions ++= Seq(
  "-Xsource:3-cross",
  "-target:23",
  "-release:23",
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
    "ch.qos.logback"     %  "logback-classic" % "1.5.17",
    "org.scalameta"      %% "scalameta" % "4.13.1.1",

    "dev.zio" %% "zio-schema" % schemaV,
    "dev.zio" %% "zio-schema-derivation" % schemaV,
    "dev.zio" %% "zio-schema-json" % schemaV,

    "com.lihaoyi" % "ammonite" % AmmoniteVersion % "test" cross CrossVersion.full
  )

Compile / sourceGenerators += genIndexDsl

javacOptions ++= Seq("-source", "23", "-target", "23")
javaHome := Some(file("/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home/"))

//test:run
Test / sourceGenerators += Def.task {
  val file = (Test / sourceManaged).value / "amm.scala"
  IO.write(file, """object amm extends App { ammonite.Main().run() }""")
  Seq(file)
}.taskValue


addCommandAlias("c", "compile")
addCommandAlias("r", "reload")