/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import java.util

import io.lenses.connect.secrets.connect.{FILE_DIR, FILE_DIR_DESC}
import org.apache.kafka.common.config.{AbstractConfig, ConfigDef}
import org.apache.kafka.common.config.ConfigDef.{Importance, Type}

object ENVProviderConfig {
  val config = new ConfigDef().define(
    FILE_DIR,
    Type.STRING,
    "",
    Importance.MEDIUM,
    FILE_DIR_DESC
  )
}

case class ENVProviderConfig(props: util.Map[String, _])
  extends AbstractConfig(ENVProviderConfig.config, props)
