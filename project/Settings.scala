/*
 * Copyright (c) 2017-2020 Lenses.io Ltd
 */

import com.eed3si9n.jarjarabrams.ShadeRule
import sbt.*
import sbt.Keys.*
import sbt.internal.util.ManagedLogger
import sbtassembly.Assembly.JarEntry
import sbtassembly.AssemblyKeys.*
import sbtassembly.CustomMergeStrategy
import sbtassembly.MergeStrategy
import sbtassembly.PathList

import java.io.*
import java.net.*
import scala.util.Try
import java.nio.file.Files
import java.nio.file.Paths

object Settings extends Dependencies {

  val scala213 = "2.13.10"
  val scala3   = "3.2.2"

  val nextVersion = "2.3.1"
  val artifactVersion: String = {
    sys.env.get("LENSES_TAG_NAME") match {
      case Some(tag) => tag
      case _         => s"$nextVersion-SNAPSHOT"
    }
  }

  object ScalacFlags {
    val FatalWarnings212     = "-Xfatal-warnings"
    val FatalWarnings213     = "-Werror"
    val WarnUnusedImports212 = "-Ywarn-unused-import"
    val WarnUnusedImports213 = "-Ywarn-unused:imports"
  }

  val modulesSettings: Seq[Setting[_]] = Seq(
    organization := "io.lenses",
    version := artifactVersion,
    scalaOrganization := "org.scala-lang",
    resolvers ++= Seq(
      Resolver.mavenLocal,
      Resolver.mavenCentral,
    ),
    crossScalaVersions := List( /*scala3, */ scala213),
    Compile / scalacOptions ++= Seq(
      "-release:11",
      "-encoding",
      "utf8",
      "-deprecation",
      "-unchecked",
      "-feature",
      "11",
    ),
    Compile / scalacOptions ++= {
      Seq(
        ScalacFlags.WarnUnusedImports213,
      )
    },
    Compile / console / scalacOptions --= Seq(
      ScalacFlags.FatalWarnings212,
      ScalacFlags.FatalWarnings213,
      ScalacFlags.WarnUnusedImports212,
      ScalacFlags.WarnUnusedImports213,
    ),
    // TODO remove for cross build
    scalaVersion := scala213,
    Test / fork := true,
  )

  implicit final class AssemblyConfigurator(project: Project) {

    /*
    Exclude the jar signing files from dependencies.  They come from other jars,
    and including these would break assembly (due to duplicates) or classloading
    (due to mismatching jar checksum/signature).
     */
    val excludeFilePatterns = Set(".MF", ".RSA", ".DSA", ".SF")

    def excludeFileFilter(p: String): Boolean =
      excludeFilePatterns.exists(p.endsWith)

    val excludePatterns = Set(
      "kafka-client",
      "kafka-connect-json",
      "hadoop-yarn",
      "org.apache.kafka",
      "zookeeper",
      "log4j",
      "junit",
    )

    def configureAssembly(): Project =
      project.settings(
        Seq(
          assembly / test := {},
          assembly / assemblyOutputPath := {
            createLibsDir((Compile / target).value)
            file(
              target.value + "/libs/" + (assembly / assemblyJarName).value,
            )
          },
          assembly / assemblyExcludedJars := {
            val cp: Classpath = (assembly / fullClasspath).value
            cp filter { f =>
              excludePatterns
                .exists(f.data.getName.contains) && (!f.data.getName
                .contains("slf4j"))
            }
          },
          assembly / assemblyMergeStrategy := {
            case PathList("META-INF", "maven", _ @_*) =>
              CustomMergeStrategy("keep-only-fresh-maven-descriptors", 1) {
                assemblyDependency =>
                  val keepDeps = assemblyDependency.collect {
                    case dependency @ (_: sbtassembly.Assembly.Project) =>
                      JarEntry(dependency.target, dependency.stream)
                  }
                  Right(keepDeps)
              }
            case PathList("META-INF", "maven", _ @_*) =>
              MergeStrategy.discard
            case PathList("META-INF", "MANIFEST.MF") =>
              MergeStrategy.discard
            case p if excludeFileFilter(p) =>
              MergeStrategy.discard
            case PathList(ps @ _*) if ps.last == "module-info.class" =>
              MergeStrategy.discard
            case _ => MergeStrategy.first
          },
          assembly / assemblyShadeRules ++= Seq(
            ShadeRule.rename("io.confluent.**" -> "lshaded.confluent.@1").inAll,
            ShadeRule.rename("com.fasterxml.**" -> "lshaded.fasterxml.@1").inAll,
          ),
        ),
      )

    def createLibsDir(targetDir: File): Unit = {

      val newDirectory = targetDir / "libs"

      if (!Files.exists(Paths.get(newDirectory.toString))) {
        Files.createDirectories(Paths.get(newDirectory.toString))
      }
    }

  }

  lazy val IntegrationTest = config("it") extend Test

  implicit final class MavenDescriptorConfigurator(project: Project) {

    val generateMetaInfMaven = taskKey[Unit]("Generate META-INF/maven directory")

    def configureMavenDescriptor(): Project =
      project
        .settings(
          generateMetaInfMaven := {
            val log = streams.value.log

            val targetDirBase = (Compile / crossTarget).value / "classes" / "META-INF" / "maven"

            val allModuleIds: Map[ModuleID, String] = update
              .value
              .configuration(Compile)
              .toVector
              .flatMap(_.modules)
              .map {
                e: ModuleReport => e.module -> e.artifacts.headOption
              }
              .collect {
                case (moduleId, Some((moduleJar, moduleFile))) =>
                  moduleId ->
                    moduleJar.url.get.toString
                      .reverse
                      .replaceFirst(".jar".reverse, ".pom".reverse)
                      .reverse
              }.toMap

            for ((moduleId, pomUrl) <- allModuleIds) {

              log.info(s"Processing ${moduleId.name}")

              val groupId    = moduleId.organization
              val artifactId = moduleId.name
              val version    = moduleId.revision
              val targetDir  = targetDirBase / groupId / artifactId
              targetDir.mkdirs()

              val propertiesFileChanged = createPomPropertiesIfChanged(groupId, artifactId, version, targetDir)
              if (propertiesFileChanged) {
                createPomXml(log, targetDir, pomUrl)
              }
            }

          },
          (Compile / compile) := ((Compile / compile) dependsOn generateMetaInfMaven).value,
        )

    private def createPomXml(log: ManagedLogger, targetDir: File, pomUrl: String): Option[File] = {
      val pomFile = targetDir / "pom.xml"

      try {
        val url        = new URL(pomUrl)
        val connection = url.openConnection().asInstanceOf[HttpURLConnection]
        connection.setRequestMethod("GET")

        if (connection.getResponseCode == HttpURLConnection.HTTP_OK && connection.getContentType == "text/xml") {
          val inputStream = connection.getInputStream
          try {
            val pomContent = new String(inputStream.readAllBytes())
            IO.write(pomFile, pomContent)
            log.info(s"Successfully retrieved and saved POM from $pomUrl to $pomFile")
            Some(pomFile)

          } finally {
            inputStream.close()
          }
        } else {
          log.error(
            s"Failed to retrieve POM from $pomUrl. HTTP Status: ${connection.getResponseCode}, Content Type: ${connection.getContentType}",
          )
          Option.empty
        }
      } catch {
        case e: MalformedURLException =>
          log.error(s"Invalid URL: $pomUrl")
          Option.empty
        case e: IOException =>
          log.error(s"Error while retrieving POM from $pomUrl: ${e.getMessage}")
          Option.empty
      }

    }

    private def createPomPropertiesIfChanged(
      groupId:    String,
      artifactId: String,
      version:    String,
      targetDir:  File,
    ): Boolean = {
      val propertiesFile = targetDir / "pom.properties"
      val propertiesContent =
        s"""version=$version
           |groupId=$groupId
           |artifactId=$artifactId
                  """.stripMargin

      val alreadyExists = Try(IO.read(propertiesFile))
        .toOption
        .contains(propertiesContent)

      if (!alreadyExists) {
        IO.write(propertiesFile, propertiesContent)
      }

      !alreadyExists

    }
  }
}
