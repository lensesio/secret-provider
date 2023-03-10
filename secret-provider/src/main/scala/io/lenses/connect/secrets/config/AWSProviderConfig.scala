/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import io.lenses.connect.secrets.connect.AuthMode
import io.lenses.connect.secrets.connect._
import org.apache.kafka.common.config.ConfigDef.Importance
import org.apache.kafka.common.config.ConfigDef.Type
import org.apache.kafka.common.config.AbstractConfig
import org.apache.kafka.common.config.ConfigDef

import java.util

object AWSProviderConfig {

  val AWS_REGION:     String = "aws.region"
  val AWS_ACCESS_KEY: String = "aws.access.key"
  val AWS_SECRET_KEY: String = "aws.secret.key"
  val AUTH_METHOD:    String = "aws.auth.method"

  val config: ConfigDef = new ConfigDef()
    .define(
      AWS_REGION,
      Type.STRING,
      Importance.HIGH,
      "AWS region the Secrets manager is in",
    )
    .define(
      AWS_ACCESS_KEY,
      Type.STRING,
      null,
      Importance.HIGH,
      "AWS access key",
    )
    .define(
      AWS_SECRET_KEY,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      "AWS password key",
    )
    .define(
      AUTH_METHOD,
      Type.STRING,
      AuthMode.CREDENTIALS.toString,
      Importance.HIGH,
      """
        | AWS authenticate method, 'credentials' to use the provided credentials 
        | or 'default' for the standard AWS provider chain.
        | Default is 'credentials'
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

case class AWSProviderConfig(props: util.Map[String, _]) extends AbstractConfig(AWSProviderConfig.config, props)
