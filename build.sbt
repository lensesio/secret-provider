import Settings.modulesSettings

name := "secret-provider"
javacOptions ++= Seq("--release", "11")
javaOptions ++= Seq("-Xms512M", "-Xmx2048M")
lazy val root = (project in file("."))
  .settings(modulesSettings)

addCommandAlias(
  "validateAll",
  ";scalafmtCheck;scalafmtSbtCheck;test:scalafmtCheck;"
)
addCommandAlias(
  "formatAll",
  ";scalafmt;scalafmtSbt;test:scalafmt;"
)
