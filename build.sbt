import Settings.modulesSettings
import Settings.secretProviderDeps
import Settings.artifactVersion
import Settings.AssemblyConfigurator
import Settings.testSinkDeps
import Settings.scala213
import sbt.Project.projectToLocalProject

name := "secret-provider"
javacOptions ++= Seq("--release", "11")
javaOptions ++= Seq("-Xms512M", "-Xmx2048M")

lazy val subProjects: Seq[Project] = Seq(
  `test-sink`,
  `secret-provider`,
)
lazy val subProjectsRefs: Seq[ProjectReference] =
  subProjects.map(projectToLocalProject)

lazy val root = (project in file("."))
  .settings(
    scalaVersion := scala213,
    publish := {},
    publishArtifact := false,
    name := "secret-provider",
  )
  .aggregate(
    subProjectsRefs: _*,
  )
  .disablePlugins(AssemblyPlugin)

lazy val `secret-provider` = (project in file("secret-provider"))
  .settings(
    modulesSettings ++
      Seq(
        name := "secret-provider",
        description := "Kafka Connect compatible connectors to move data between Kafka and popular data stores",
        publish / skip := true,
        libraryDependencies ++= secretProviderDeps,
      ),
  )
  .configureAssembly()
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)

lazy val `test-sink` = (project in file("test-sink"))
  .settings(
    modulesSettings ++
      Seq(
        name := "test-sink",
        description := "Kafka Connect compatible connectors to move data between Kafka and popular data stores",
        publish / skip := true,
        libraryDependencies ++= testSinkDeps,
      ),
  )
  .configureAssembly()

addCommandAlias(
  "validateAll",
  ";scalafmtCheck;scalafmtSbtCheck;test:scalafmtCheck;it:scalafmtCheck;",
)
addCommandAlias(
  "formatAll",
  ";scalafmt;scalafmtSbt;test:scalafmt;it:scalafmt;",
)
