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
  credentials:      Option[AWSCredentials],
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

    val endpointOverride =
      Try(configs.getString(AWSProviderConfig.ENDPOINT_OVERRIDE)).toOption.filterNot(_.trim.isEmpty)
    val authMode =
      getAuthenticationMethod(configs.getString(AWSProviderConfig.AUTH_METHOD))

    val awsCredentials: Option[AWSCredentials] = Option.when(authMode == AuthMode.CREDENTIALS) {
      AWSCredentials(configs).left.map(throw _).merge
    }

    val secretType = SecretTypeConfig.lookupAndValidateSecretTypeValue(configs.getString)

    new AWSProviderSettings(
      region         = region,
      credentials    = awsCredentials,
      authMode       = authMode,
      fileWriterOpts = FileWriterOptions(configs),
      defaultTtl =
        Option(configs.getLong(SECRET_DEFAULT_TTL).toLong).filterNot(_ == 0L).map(Duration.of(_, ChronoUnit.MILLIS)),
      endpointOverride = endpointOverride,
      secretType       = secretType,
    )
  }
}
