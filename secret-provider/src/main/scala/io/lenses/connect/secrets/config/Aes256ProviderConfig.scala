package io.lenses.connect.secrets.config

import io.lenses.connect.secrets.connect.FILE_DIR
import io.lenses.connect.secrets.connect.FILE_DIR_DESC
import org.apache.kafka.common.config.ConfigDef.Importance
import org.apache.kafka.common.config.ConfigDef.Type
import org.apache.kafka.common.config.AbstractConfig
import org.apache.kafka.common.config.ConfigDef

import java.util

object Aes256ProviderConfig {
  val SECRET_KEY = "aes256.key"

  val config = new ConfigDef()
    .define(
      SECRET_KEY,
      Type.STRING,
      "",
      Importance.MEDIUM,
      "Key used to decode AES256 encoded values",
    )
    .define(
      FILE_DIR,
      Type.STRING,
      "",
      Importance.MEDIUM,
      FILE_DIR_DESC,
    )
}

case class Aes256ProviderConfig(props: util.Map[String, _]) extends AbstractConfig(Aes256ProviderConfig.config, props) {
  def aes256Key:      String = getString(Aes256ProviderConfig.SECRET_KEY)
  def writeDirectory: String = getString(FILE_DIR)
}
