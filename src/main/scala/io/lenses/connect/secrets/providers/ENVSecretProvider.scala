/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import java.util

import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider

import scala.collection.JavaConverters._

class ENVSecretProvider extends ConfigProvider {

  var vars = Map.empty[String, String]

  override def get(path: String): ConfigData =
    new ConfigData(Map.empty[String, String].asJava)

  override def get(path: String, keys: util.Set[String]): ConfigData = {
    val data = vars
      .filterKeys(keys.contains)
      .toMap
      .asJava

    new ConfigData(data)
  }

  override def configure(configs: util.Map[String, _]): Unit = {
    vars = System.getenv().asScala.toMap
  }

  override def close(): Unit = {}
}
