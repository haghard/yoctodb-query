addSbtPlugin("com.scalapenos"     %   "sbt-prompt"      % "1.0.2")
addSbtPlugin("org.scalameta"      %   "sbt-scalafmt"    % "2.5.6")

libraryDependencies ++=
  Seq(
    "org.scalameta" %% "scalameta" % "4.15.2",
    "com.yandex.yoctodb" % "yoctodb-core" % "0.0.20",
  )

