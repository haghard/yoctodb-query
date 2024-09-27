// We need scalameta to be available for /bespoke-plugin.sbt, so we put it one level up
libraryDependencies ++=
  Seq(
    "org.scalameta" %% "scalameta" % "4.9.9",
    "com.yandex.yoctodb" % "yoctodb-core" % "0.0.20",
  )

