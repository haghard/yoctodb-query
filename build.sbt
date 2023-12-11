scalaVersion := "2.13.12"

name := "yoctodb-query"

libraryDependencies ++=
  Seq(
    "com.yandex.yoctodb" % "yoctodb-core" % "0.0.20",
    "ch.qos.logback"     %  "logback-classic" % "1.4.7"
  )

addCommandAlias("c", "compile")
addCommandAlias("r", "reload")