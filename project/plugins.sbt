addSbtPlugin("com.scalapenos"     %   "sbt-prompt"      % "1.0.2")
addSbtPlugin("org.scalameta"      %   "sbt-scalafmt"    % "2.5.5")

libraryDependencies ++=
  Seq(
    "org.scalameta" %% "scalameta" % "4.13.9",
    "com.yandex.yoctodb" % "yoctodb-core" % "0.0.20",
  )

