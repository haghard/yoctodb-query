scalaVersion := "2.13.13"

name := "yoctodb-query"

libraryDependencies ++=
  Seq(
    "com.yandex.yoctodb" % "yoctodb-core" % "0.0.20",
    "ch.qos.logback"     %  "logback-classic" % "1.4.14"
  )

addCommandAlias("c", "compile")
addCommandAlias("r", "reload")