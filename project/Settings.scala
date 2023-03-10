/*
 * Copyright (c) 2017-2020 Lenses.io Ltd
 */

import sbt.Keys._
import sbt.Def
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.MergeStrategy

object Settings extends Dependencies {

  val scala212 = "2.12.14"
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
    crossScalaVersions := List( /*scala3, */ scala213 /*scala212*/ ),
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
    Test / fork := true,
  )

  implicit final class AssemblyConfigurator(project: Project) {
    def configureAssembly(): Project =
      project.settings(
        assembly / assemblyJarName := s"secret-provider_${SemanticVersioning(scalaVersion.value).majorMinor}-${artifactVersion}-all.jar",
        assembly / assemblyExcludedJars := {
          val cp = (assembly / fullClasspath).value
          val excludes = Set(
            "org.apache.avro",
            "org.apache.kafka",
            "io.confluent",
            "org.apache.zookeeper",
          )
          cp filter { f => excludes.exists(f.data.getName.contains(_)) }
        },
        assembly / assemblyMergeStrategy := {
          case "module-info.class" => MergeStrategy.discard
          case x if x.contains("io.netty.versions.properties") =>
            MergeStrategy.concat
          case x =>
            val oldStrategy = (assembly / assemblyMergeStrategy).value
            oldStrategy(x)
        },
      )

  }

}
