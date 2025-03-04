addSbtPlugin("com.scalapenos"     %   "sbt-prompt"      % "1.0.2")
addSbtPlugin("org.scalameta"      %   "sbt-scalafmt"    % "2.5.4")

libraryDependencies ++=
  Seq(
    "org.scalameta" %% "scalameta" % "4.13.1.1",
    "com.yandex.yoctodb" % "yoctodb-core" % "0.0.20",
  )

