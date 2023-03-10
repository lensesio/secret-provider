/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.secretsmanager.model.DescribeSecretRequest
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import com.amazonaws.services.secretsmanager.AWSSecretsManager
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.config.AWSProviderSettings
import io.lenses.connect.secrets.connect.AuthMode
import io.lenses.connect.secrets.connect.decodeKey
import io.lenses.connect.secrets.io.FileWriter
import io.lenses.connect.secrets.io.FileWriterOnce
import io.lenses.connect.secrets.utils.EncodingAndId
import org.apache.kafka.connect.errors.ConnectException

import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.Calendar
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait AWSHelper extends StrictLogging {

  // initialize the AWS client based on the auth mode
  def createClient(settings: AWSProviderSettings): AWSSecretsManager = {

    logger.info(
      s"Initializing client with mode [${settings.authMode}]",
    )

    val credentialProvider = settings.authMode match {
      case AuthMode.CREDENTIALS =>
        new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(
            settings.accessKey,
            settings.secretKey.value(),
          ),
        )
      case _ =>
        new DefaultAWSCredentialsProviderChain()
    }

    AWSSecretsManagerClientBuilder
      .standard()
      .withCredentials(credentialProvider)
      .withRegion(settings.region)
      .build()

  }

  // determine the ttl for the secret
  private def getTTL(
    client:   AWSSecretsManager,
    secretId: String,
  ): Option[OffsetDateTime] = {

    // describe to get the ttl
    val descRequest: DescribeSecretRequest =
      new DescribeSecretRequest().withSecretId(secretId)

    Try(client.describeSecret(descRequest)) match {
      case Success(d) =>
        if (d.getRotationEnabled) {
          val lastRotation = d.getLastRotatedDate
          val nextRotationInDays =
            d.getRotationRules.getAutomaticallyAfterDays
          val cal = Calendar.getInstance()
          //set to last rotation date
          cal.setTime(lastRotation)
          //increment
          cal.add(Calendar.DAY_OF_MONTH, nextRotationInDays.toInt)
          Some(
            OffsetDateTime.ofInstant(cal.toInstant, cal.getTimeZone.toZoneId),
          )

        } else None

      case Failure(exception) =>
        throw new ConnectException(
          s"Failed to describe secret [$secretId]",
          exception,
        )
    }
  }

  // get the key value and ttl in the specified secret
  def getSecretValue(
    client:   AWSSecretsManager,
    rootDir:  String,
    secretId: String,
    key:      String,
  ): (String, Option[OffsetDateTime]) =
    // get the secret
    Try(
      client.getSecretValue(new GetSecretValueRequest().withSecretId(secretId)),
    ) match {
      case Success(secret) =>
        val value =
          new ObjectMapper()
            .readValue(
              secret.getSecretString,
              classOf[java.util.HashMap[String, String]],
            )
            .asScala
            .getOrElse(
              key,
              throw new ConnectException(
                s"Failed to look up key [$key] in secret [${secret.getName}]. key not found",
              ),
            )

        val fileWriter: FileWriter = new FileWriterOnce(
          Paths.get(rootDir, secretId),
        )
        // decode the value
        val encodingAndId = EncodingAndId.from(key)
        (
          decodeKey(
            key      = key,
            value    = value,
            encoding = encodingAndId.encoding,
            writeFileFn = content => {
              fileWriter.write(key.toLowerCase, content, key).toString
            },
          ),
          getTTL(client, secretId),
        )

      case Failure(exception) =>
        throw new ConnectException(
          s"Failed to look up key [$key] in secret [$secretId] due to [${exception.getMessage}]",
          exception,
        )
    }
}
