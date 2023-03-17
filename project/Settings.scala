/*
 * Copyright (c) 2017-2020 Lenses.io Ltd
 */

import com.eed3si9n.jarjarabrams.ShadeRule
import sbt.Keys._
import sbt.Def
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.MergeStrategy
import sbtassembly.PathList

object Settings extends Dependencies {

  val scala213 = "2.13.10"
  val scala3   = "3.2.2"

  val nextVersion = "2.1.7"
  val artifactVersion = {
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
          assembly / assemblyOutputPath := file(
            target.value + "/libs/" + (assembly / assemblyJarName).value,
          ),
          assembly / assemblyExcludedJars := {
            val cp: Classpath = (assembly / fullClasspath).value
            cp filter { f =>
              excludePatterns
                .exists(f.data.getName.contains) && (!f.data.getName
                .contains("slf4j"))
            }
          },
          assembly / assemblyMergeStrategy := {
            case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
            case p if excludeFileFilter(p)           => MergeStrategy.discard
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

  }

  lazy val IntegrationTest = config("it") extend Test

}
