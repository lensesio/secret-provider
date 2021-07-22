import Settings.modulesSettings

name := "secret-provider"

javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:+CMSClassUnloadingEnabled")
lazy val root = (project in file("."))
  .settings(modulesSettings)




