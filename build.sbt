name := "yoctodb-query"
scalaVersion := "2.13.18"

val AmmoniteVersion = "3.0.9"

//lazy val requiredJvmVersion = sys.props("java.specification.version")
val requiredJvmVersion = "25"

initialize := {
  val _ = initialize.value
  val current  = sys.props("java.specification.version")
  if (current != requiredJvmVersion)
    sys.error(s"Java $requiredJvmVersion is required for this project. Found $current instead.")
}

Compile / scalacOptions ++= Seq(
  "-Xsource:3-cross",
  s"-release:$requiredJvmVersion",
  s"-Wconf:src=${(Compile / target).value}/scala-2.13/src_managed/.*:silent",
  "-Wconf:msg=Marked as deprecated in proto file:silent",
  "-Vtype-diffs",
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
  "-Wconf:cat=unused-imports:s", //suppress all unused import warnings
  "-Xfatal-warnings",
)

scalafmtOnCompile := true

libraryDependencies ++=
  Seq(
    "com.yandex.yoctodb" % "yoctodb-core" % "0.0.20",
    "ch.qos.logback"     %  "logback-classic" % "1.5.32",
    "org.scalameta"      %% "scalameta" % "4.16.1",
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
    "com.lihaoyi" % "ammonite" % AmmoniteVersion % "test" cross CrossVersion.full
  )

javaOptions ++= Seq(
  "-XX:+PrintCommandLineFlags",
  "-XshowSettings:system",
  "-XX:NativeMemoryTracking=summary", //detail|summary
  "-XX:+AlwaysPreTouch",
  "-XX:-UseAdaptiveSizePolicy",   //heap never resizes
  "-XX:MaxDirectMemorySize=128m", //Will get an error if allocate more mem for direct byte buffers
  "-Xms128m",
  "-Xmx256m",
  "-XX:+UseParallelGC",
  "-XX:ActiveProcessorCount=4",
  "--add-opens",
  "java.base/java.nio=ALL-UNNAMED",
  "--add-opens",
  "java.base/sun.nio.ch=ALL-UNNAMED",
)

javacOptions ++= Seq("-source", requiredJvmVersion, "-target", requiredJvmVersion)
javaHome := Some(file(s"/Library/Java/JavaVirtualMachines/jdk-$requiredJvmVersion.jdk/Contents/Home/"))

// test:run
run / fork := true

//test:run
Test / sourceGenerators += Def.task {
  val file = (Test / sourceManaged).value / "amm.scala"
  IO.write(file, """object amm extends App { ammonite.Main().run() }""")
  Seq(file)
}.taskValue


Compile / sourceGenerators += IndexGeneratorPlugin.autoImport.genIndexDsl
Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value)

enablePlugins(IndexGeneratorPlugin, BuildInfoPlugin)

buildInfoOptions += BuildInfoOption.ConstantValue
buildInfoKeys ++= Seq[BuildInfoKey]("rootDir" -> baseDirectory.value.toString)
buildInfoPackage := "query.dsl"

addCommandAlias("c", "compile")
addCommandAlias("r", "reload")

//++2.13.18
//show javacOptions
//show scalacOptions
