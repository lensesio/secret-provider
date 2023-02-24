import Settings.modulesSettings
import Settings.secretProviderDeps
import Settings.AssemblyConfigurator
import sbt.Project.projectToLocalProject

name := "secret-provider"
javacOptions ++= Seq("--release", "11")
javaOptions ++= Seq("-Xms512M", "-Xmx2048M")


lazy val subProjects: Seq[Project] = Seq(
  `secret-provider`
)
lazy val subProjectsRefs: Seq[ProjectReference] = subProjects.map(projectToLocalProject)

lazy val root = (project in file("."))
  .settings(
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

addCommandAlias(
  "validateAll",
  ";scalafmtCheck;scalafmtSbtCheck;test:scalafmtCheck;"
)
addCommandAlias(
  "formatAll",
  ";scalafmt;scalafmtSbt;test:scalafmt;"
)
