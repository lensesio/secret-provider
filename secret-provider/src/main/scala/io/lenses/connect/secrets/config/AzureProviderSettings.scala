/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.connect.AuthMode.AuthMode
import io.lenses.connect.secrets.connect._
import org.apache.kafka.common.config.types.Password
import org.apache.kafka.connect.errors.ConnectException

case class AzureProviderSettings(
  clientId: String,
  tenantId: String,
  secretId: Password,
  authMode: AuthMode,
  fileDir:  String,
)

import io.lenses.connect.secrets.config.AbstractConfigExtensions._
object AzureProviderSettings extends StrictLogging {
  def apply(config: AzureProviderConfig): AzureProviderSettings = {

    val authMode = getAuthenticationMethod(
      config.getString(AzureProviderConfig.AUTH_METHOD),
    )

    if (authMode == AuthMode.CREDENTIALS) {
      val clientId =
        config.getStringOrThrowOnNull(AzureProviderConfig.AZURE_CLIENT_ID)
      val tenantId =
        config.getStringOrThrowOnNull(AzureProviderConfig.AZURE_TENANT_ID)
      val secretId =
        config.getPasswordOrThrowOnNull(AzureProviderConfig.AZURE_SECRET_ID)

      if (clientId.isEmpty)
        throw new ConnectException(
          s"${AzureProviderConfig.AZURE_CLIENT_ID} not set",
        )
      if (tenantId.isEmpty)
        throw new ConnectException(
          s"${AzureProviderConfig.AZURE_TENANT_ID} not set",
        )
      if (secretId.value().isEmpty)
        throw new ConnectException(
          s"${AzureProviderConfig.AZURE_SECRET_ID} not set",
        )
    }

    val fileDir = config.getString(FILE_DIR)

    AzureProviderSettings(
      clientId = config.getString(AzureProviderConfig.AZURE_CLIENT_ID),
      tenantId = config.getString(AzureProviderConfig.AZURE_TENANT_ID),
      secretId = config.getPassword(AzureProviderConfig.AZURE_SECRET_ID),
      authMode = authMode,
      fileDir  = fileDir,
    )
  }
}
