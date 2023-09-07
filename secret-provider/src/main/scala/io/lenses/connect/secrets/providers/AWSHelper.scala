/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.cache.Ttl
import io.lenses.connect.secrets.cache.ValueWithTtl
import io.lenses.connect.secrets.config.AWSProviderSettings
import io.lenses.connect.secrets.connect.AuthMode
import io.lenses.connect.secrets.connect.decodeKey
import io.lenses.connect.secrets.io.FileWriter
import io.lenses.connect.secrets.utils.EncodingAndId
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest

import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class AWSHelper(
  client:             SecretsManagerClient,
  defaultTtl:         Option[Duration],
  altRegion:          String,
  fileWriterCreateFn: () => Option[FileWriter]
)(
  implicit
  clock: Clock,
) extends SecretHelper
    with LazyLogging {

  private val objectMapper = new ObjectMapper()

  // get the key value and ttl in the specified secret
  override def lookup(secretId: String): Either[Throwable, ValueWithTtl[Map[String, String]]] = {
    val hasAccount = secretId.indexOf("$")
    val secret_array = secretId.split("\\$")
    val region = if (altRegion.length > 0) altRegion else ""
    val secretName = if (hasAccount > -1) s"arn:aws:secretsmanager:${region}:${secret_array(0)}:secret:${secret_array(1)}" else secretId
    for {
      secretTtl         <- getTTL(secretName)
      secretValue       <- getSecretValue(secretName)
      parsedSecretValue <- parseSecretValue(secretValue)
    } yield ValueWithTtl(secretTtl, parsedSecretValue)
  }

  // determine the ttl for the secret
  def getTTL(
    secretId: String,
  )(
    implicit
    clock: Clock,
  ): Either[Throwable, Option[Ttl]] = {

    // describe to get the ttl
    val descRequest: DescribeSecretRequest =
      DescribeSecretRequest.builder().secretId(secretId).build()

    for {
      secretResponse <- Try(client.describeSecret(descRequest)).toEither
    } yield {
      if (secretResponse.rotationEnabled()) {
        //val lastRotation = secretResponse.lastRotatedDate()
        val ttlExpires       = secretResponse.nextRotationDate()
        val rotationDuration = Duration.of(secretResponse.rotationRules().automaticallyAfterDays(), ChronoUnit.DAYS)
        Some(Ttl(
          rotationDuration,
          OffsetDateTime.ofInstant(ttlExpires, ZoneId.systemDefault()),
        ))
      } else {
        Ttl(Option.empty, defaultTtl)
      }
    }

  }

  private def parseSecretValue(
    secretValues: Map[String, String],
  ): Either[Throwable, Map[String, String]] = {
    val fileWriterMaybe = fileWriterCreateFn()
    Try(
      secretValues.map {
        case (k, v) =>
          (k,
           decodeKey(
             key      = k,
             value    = v,
             encoding = EncodingAndId.from(k).encoding,
             writeFileFn = content => {
               fileWriterMaybe.fold("nofile")(_.write(k.toLowerCase, content, k).toString)
             },
           ),
          )
      },
    ).toEither
  }

  private def getSecretValue(secretId: String): Either[Throwable, Map[String, String]] = {
    val a = for {
      secretValueResult <- Try(
        client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build()),
      )
      secretString <- Try(secretValueResult.secretString())
      mapFromResponse <- Try(
        objectMapper
          .readValue(
            secretString,
            classOf[util.HashMap[String, String]],
          )
          .asScala.toMap,
      )
    } yield mapFromResponse
    a match {
      case Failure(exception) => Left(exception)
      case Success(value)     => Right(value)
    }

  }
}

object AWSHelper extends StrictLogging {

  // initialize the AWS client based on the auth mode
  def createClient(settings: AWSProviderSettings): SecretsManagerClient = {

    logger.info(
      s"Initializing client with mode [${settings.authMode}]",
    )

    val credentialProvider = settings.authMode match {
      case AuthMode.CREDENTIALS =>
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
            settings.accessKey,
            settings.secretKey.value(),
          ),
        )
      case _ =>
        DefaultCredentialsProvider.create()
    }

    val builder = SecretsManagerClient.builder()
      .credentialsProvider(credentialProvider)
      .region(Region.of(settings.region))

    settings.endpointOverride.foreach(eo => builder.endpointOverride(URI.create(eo)))

    builder.build()
  }
}
