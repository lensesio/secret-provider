/*
 * Copyright (c) 2017-2020 Lenses.io Ltd
 */

import sbt._

trait Dependencies {

  object Versions {

    val scalaLoggingVersion = "3.9.4"
    val kafkaVersion = "2.8.0"
    val vaultVersion = "5.1.0"
    val azureKeyVaultVersion = "4.1.1"
    val azureIdentityVersion = "1.0.5"
    val awsSecretsVersion = "1.12.29"

    //test
    val scalaTestVersion = "3.1.1"
    val mockitoVersion = "1.13.0"
    val byteBuddyVersion = "1.11.9"
    val slf4jVersion = "1.7.26"
    val commonsIOVersion = "1.3.2"
    val jettyVersion = "9.4.19.v20190610"
    val testContainersVersion = "1.12.3"
    val flexmarkVersion = "0.35.10"

    val scalaCollectionCompatVersion = "2.5.0"

  }

  object Dependencies {

    import Versions._

    val `scala-logging` = "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion
    val `kafka-connect-api` = "org.apache.kafka" % "connect-api" % kafkaVersion
    val `vault-java-driver` = "com.bettercloud" % "vault-java-driver" % vaultVersion
    val `azure-key-vault` = "com.azure" % "azure-security-keyvault-secrets" % azureKeyVaultVersion
    val `azure-identity` = "com.azure" % "azure-identity" % azureIdentityVersion
    val `aws-secrets-manager` = "com.amazonaws" % "aws-java-sdk-secretsmanager" % awsSecretsVersion

    val `mockito` = "org.mockito" %% "mockito-scala" % mockitoVersion
    val `scalatest` = "org.scalatest" %% "scalatest" % scalaTestVersion
    val `jetty` = "org.eclipse.jetty" % "jetty-server" % jettyVersion
    val `commons-io` = "org.apache.commons" % "commons-io" % commonsIOVersion
    val `flexmark` = "com.vladsch.flexmark" % "flexmark-all" % flexmarkVersion
    val `slf4j-api`   = "org.slf4j"          % "slf4j-api"   % slf4jVersion
    val `slf4j-simple`   = "org.slf4j"          % "slf4j-simple"   % slf4jVersion

    val `byteBuddy` = "net.bytebuddy" % "byte-buddy" % byteBuddyVersion
    val `scalaCollectionCompat` = "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion

  }

  import Dependencies._
  val secretProviderDeps = Seq(
  `scala-logging`,
  `kafka-connect-api` % Provided,
  `vault-java-driver`,
  `azure-key-vault`,
  `azure-identity` exclude("javax.activation", "activation"),
  `aws-secrets-manager`,
  `scalaCollectionCompat`,
  `mockito` % Test,
  `byteBuddy` % Test,
  `scalatest` % Test,
  `jetty` % Test,
  `commons-io` % Test,
  `flexmark` % Test,
  `slf4j-api` % Test,
  `slf4j-simple` % Test,
  )

}
