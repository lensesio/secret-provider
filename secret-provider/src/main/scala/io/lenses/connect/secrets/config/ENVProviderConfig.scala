/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import io.lenses.connect.secrets.connect.FILE_DIR
import io.lenses.connect.secrets.connect.FILE_DIR_DESC
import org.apache.kafka.common.config.ConfigDef.Importance
import org.apache.kafka.common.config.ConfigDef.Type
import org.apache.kafka.common.config.AbstractConfig
import org.apache.kafka.common.config.ConfigDef

import java.util

object ENVProviderConfig {
  val config = new ConfigDef().define(
    FILE_DIR,
    Type.STRING,
    "",
    Importance.MEDIUM,
    FILE_DIR_DESC,
  )
}

case class ENVProviderConfig(props: util.Map[String, _]) extends AbstractConfig(ENVProviderConfig.config, props)
