/*
 * Copyright (c) 2017-2020 Lenses.io Ltd
 */

import sbt._

trait Dependencies {

  object Versions {

    val scalaLoggingVersion  = "3.9.5"
    val kafkaVersion         = "3.4.0"
    val vaultVersion         = "5.3.0"
    val azureKeyVaultVersion = "4.5.2"
    val azureIdentityVersion = "1.8.1"

    val awsSdkV2Version = "2.20.26"

    //test
    val scalaTestVersion = "3.2.15"
    val mockitoVersion   = "3.2.15.0"
    val byteBuddyVersion = "1.14.2"
    val slf4jVersion     = "2.0.5"
    val commonsIOVersion = "1.3.2"
    val jettyVersion     = "11.0.14"
    val flexmarkVersion  = "0.64.0"
    val jsonSmartVersion = "2.5.0"

    val scalaCollectionCompatVersion = "2.8.1"
    val jakartaServletVersion        = "6.0.0"
    val testContainersVersion        = "1.17.6"
    val json4sVersion                = "4.0.6"
    val catsEffectVersion            = "3.4.8"

  }

  object Dependencies {

    import Versions._

    val `scala-logging` =
      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion
    val `kafka-connect-api` = "org.apache.kafka" % "connect-api" % kafkaVersion
    val `vault-java-driver` =
      "io.github.jopenlibs" % "vault-java-driver" % vaultVersion
    val `azure-key-vault` =
      "com.azure" % "azure-security-keyvault-secrets" % azureKeyVaultVersion
    val `azure-identity` = "com.azure" % "azure-identity" % azureIdentityVersion

    lazy val awsSecretsManagerSdkV2 = "software.amazon.awssdk" % "secretsmanager" % awsSdkV2Version
    lazy val awsIamSdkV2            = "software.amazon.awssdk" % "iam"            % awsSdkV2Version
    lazy val awsStsSdkV2            = "software.amazon.awssdk" % "sts"            % awsSdkV2Version

    val `mockito`      = "org.scalatestplus"   %% "mockito-4-6"  % mockitoVersion
    val `scalatest`    = "org.scalatest"       %% "scalatest"    % scalaTestVersion
    val `jetty`        = "org.eclipse.jetty"    % "jetty-server" % jettyVersion
    val `commons-io`   = "org.apache.commons"   % "commons-io"   % commonsIOVersion
    val `flexmark`     = "com.vladsch.flexmark" % "flexmark-all" % flexmarkVersion
    val `slf4j-api`    = "org.slf4j"            % "slf4j-api"    % slf4jVersion
    val `slf4j-simple` = "org.slf4j"            % "slf4j-simple" % slf4jVersion

    val `byteBuddy` = "net.bytebuddy" % "byte-buddy" % byteBuddyVersion
    val `scalaCollectionCompat` =
      "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion
    val `jakartaServlet` =
      "jakarta.servlet" % "jakarta.servlet-api" % jakartaServletVersion

    val `testContainersCore` =
      "org.testcontainers" % "testcontainers" % testContainersVersion
    val `testContainersKafka` =
      "org.testcontainers" % "kafka" % testContainersVersion
    val `testContainersVault` =
      "org.testcontainers" % "vault" % testContainersVersion

    val `json4sNative`  = "org.json4s"    %% "json4s-native"  % json4sVersion
    val `json4sJackson` = "org.json4s"    %% "json4s-jackson" % json4sVersion
    val `cats`          = "org.typelevel" %% "cats-effect"    % catsEffectVersion

    val `jsonSmart`= "net.minidev" % "json-smart" % jsonSmartVersion

  }

  import Dependencies._
  val secretProviderDeps = Seq(
    `scala-logging`,
    `jsonSmart`,
    `kafka-connect-api` % Provided,
    `vault-java-driver`,
    `azure-key-vault` exclude ("net.minidev", "json-smart"),
    `azure-identity` exclude ("javax.activation", "activation") exclude ("net.minidev", "json-smart"),
    `awsSecretsManagerSdkV2`,
    `awsStsSdkV2`,
    `awsIamSdkV2`         % IntegrationTest,
    `jakartaServlet`      % Test,
    `mockito`             % Test,
    `byteBuddy`           % Test,
    `scalatest`           % Test,
    `jetty`               % Test,
    `commons-io`          % Test,
    `flexmark`            % Test,
    `slf4j-api`           % Test,
    `slf4j-simple`        % Test,
    `cats`                % IntegrationTest,
    `testContainersCore`  % IntegrationTest,
    `testContainersKafka` % IntegrationTest,
    `testContainersVault` % IntegrationTest,
    `json4sNative`        % IntegrationTest,
  )

  val testSinkDeps = Seq(
    `scala-logging`,
    `kafka-connect-api` % Provided,
    `vault-java-driver`,
    `awsSecretsManagerSdkV2`,
    `json4sNative`,
    `scalatest` % Test,
  )

}
