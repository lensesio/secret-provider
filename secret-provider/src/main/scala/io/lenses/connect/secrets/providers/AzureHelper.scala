/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.azure.core.credential.TokenCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.security.keyvault.secrets.SecretClient
import com.azure.security.keyvault.secrets.models.SecretProperties
import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.config.AzureProviderSettings
import io.lenses.connect.secrets.connect
import io.lenses.connect.secrets.connect.Encoding.Encoding
import io.lenses.connect.secrets.connect._
import org.apache.kafka.connect.errors.ConnectException

import java.nio.file.FileSystems
import java.time.OffsetDateTime
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait AzureHelper extends StrictLogging {

  private val separator: String = FileSystems.getDefault.getSeparator

  // look up secret in Azure
  def getSecretValue(
    rootDir: String,
    path:    String,
    client:  SecretClient,
    key:     String,
  ): (String, Option[OffsetDateTime]) =
    Try(client.getSecret(key)) match {
      case Success(secret) =>
        val value = secret.getValue
        val props = secret.getProperties

        val content = retrieveEncodingFromTags(props) match {
          case Encoding.UTF8 =>
            value

          case Encoding.UTF8_FILE =>
            val fileName =
              getFileName(rootDir, path, key.toLowerCase, separator)
            fileWriter(
              fileName,
              value.getBytes,
              key.toLowerCase,
            )
            fileName

          case Encoding.BASE64 =>
            decode(key, value)

          // write to file and set the file name as the value
          case Encoding.BASE64_FILE | Encoding.UTF8_FILE =>
            val fileName =
              getFileName(rootDir, path, key.toLowerCase, separator)
            val decoded = decodeToBytes(key, value)
            fileWriter(
              fileName,
              decoded,
              key.toLowerCase,
            )
            fileName
        }

        val expiry = Option(props.getExpiresOn)
        (content, expiry)

      case Failure(e) =>
        throw new ConnectException(
          s"Failed to look up secret [$key] at [${client.getVaultUrl}]",
          e,
        )
    }

  private def retrieveEncodingFromTags(props: SecretProperties): connect.Encoding.Value =
    // check the file-encoding
    {
      for {
        propsMap                 <- Option(props.getTags.asScala)
        fileEncodingFromPropsMap <- propsMap.get(FILE_ENCODING)
        enc                      <- Encoding.withoutHyphensInsensitiveOpt(fileEncodingFromPropsMap)
      } yield enc
    }.getOrElse(Encoding.UTF8)

  // setup azure credentials
  def createCredentials(settings: AzureProviderSettings): TokenCredential = {

    logger.info(
      s"Initializing client with mode [${settings.authMode.toString}]",
    )

    settings.authMode match {
      case AuthMode.CREDENTIALS =>
        new ClientSecretCredentialBuilder()
          .clientId(settings.clientId)
          .clientSecret(settings.secretId.value())
          .tenantId(settings.tenantId)
          .build()

      case _ =>
        new DefaultAzureCredentialBuilder().build()

    }
  }
}
