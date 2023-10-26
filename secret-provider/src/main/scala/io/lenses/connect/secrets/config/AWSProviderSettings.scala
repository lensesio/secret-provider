/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import io.lenses.connect.secrets.config.SecretType.SecretType
import io.lenses.connect.secrets.connect.AuthMode.AuthMode
import io.lenses.connect.secrets.connect._
import org.apache.kafka.common.config.types.Password
import org.apache.kafka.connect.errors.ConnectException

import java.time.Duration
import java.time.temporal.ChronoUnit
import scala.util.Try

case class AWSProviderSettings(
  region:           String,
  accessKey:        String,
  secretKey:        Password,
  authMode:         AuthMode,
  fileWriterOpts:   Option[FileWriterOptions],
  defaultTtl:       Option[Duration],
  endpointOverride: Option[String],
  secretType:       SecretType,
)

import io.lenses.connect.secrets.config.AbstractConfigExtensions._
object AWSProviderSettings {
  def apply(configs: AWSProviderConfig): AWSProviderSettings = {
    // TODO: Validate all configs in one step and provide all errors together
    val region = configs.getStringOrThrowOnNull(AWSProviderConfig.AWS_REGION)
    val accessKey =
      configs.getStringOrThrowOnNull(AWSProviderConfig.AWS_ACCESS_KEY)
    val secretKey =
      configs.getPasswordOrThrowOnNull(AWSProviderConfig.AWS_SECRET_KEY)

    val endpointOverride =
      Try(configs.getString(AWSProviderConfig.ENDPOINT_OVERRIDE)).toOption.filterNot(_.trim.isEmpty)
    val authMode =
      getAuthenticationMethod(configs.getString(AWSProviderConfig.AUTH_METHOD))

    if (authMode == AuthMode.CREDENTIALS) {
      if (accessKey.isEmpty)
        throw new ConnectException(
          s"${AWSProviderConfig.AWS_ACCESS_KEY} not set",
        )
      if (secretKey.value().isEmpty)
        throw new ConnectException(
          s"${AWSProviderConfig.AWS_SECRET_KEY} not set",
        )
    }

    val secretType = SecretTypeConfig.lookupAndValidateSecretTypeValue(configs.getString)

    new AWSProviderSettings(
      region         = region,
      accessKey      = accessKey,
      secretKey      = secretKey,
      authMode       = authMode,
      fileWriterOpts = FileWriterOptions(configs),
      defaultTtl =
        Option(configs.getLong(SECRET_DEFAULT_TTL).toLong).filterNot(_ == 0L).map(Duration.of(_, ChronoUnit.MILLIS)),
      endpointOverride = endpointOverride,
      secretType       = secretType,
    )
  }
}
