/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import io.lenses.connect.secrets.connect._
import org.apache.kafka.common.config.ConfigDef.Importance
import org.apache.kafka.common.config.ConfigDef.Type
import org.apache.kafka.common.config.AbstractConfig
import org.apache.kafka.common.config.ConfigDef

import java.util

object AzureProviderConfig {

  val AZURE_CLIENT_ID = "azure.client.id"
  val AZURE_TENANT_ID = "azure.tenant.id"
  val AZURE_SECRET_ID = "azure.secret.id"
  val AUTH_METHOD     = "azure.auth.method"

  val config: ConfigDef = new ConfigDef()
    .define(
      AZURE_CLIENT_ID,
      Type.STRING,
      null,
      Importance.HIGH,
      "Azure client id for the service principal",
    )
    .define(
      AZURE_TENANT_ID,
      Type.STRING,
      null,
      Importance.HIGH,
      "Azure tenant id for the service principal",
    )
    .define(
      AZURE_SECRET_ID,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      "Azure secret id for the service principal",
    )
    .define(
      AUTH_METHOD,
      Type.STRING,
      AuthMode.CREDENTIALS.toString,
      Importance.MEDIUM,
      """
        |Azure authenticate method, 'credentials' to use the provided credentials or 
        |'default' for the standard Azure provider chain
        |Default is 'credentials'
        |""".stripMargin,
    )
    .define(
      FILE_DIR,
      Type.STRING,
      "",
      Importance.MEDIUM,
      FILE_DIR_DESC,
    )
}

case class AzureProviderConfig(props: util.Map[String, _]) extends AbstractConfig(AzureProviderConfig.config, props)
