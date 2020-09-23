package io.lenses.connect.secrets.config

import org.apache.kafka.common.config.{AbstractConfig, ConfigDef}
import org.apache.kafka.common.config.ConfigDef.{Importance, Type}
import java.util

object Aes256ProviderConfig {
  val SECRET_KEY = "aes256.key"
  
  val config = new ConfigDef().define(
    SECRET_KEY,
    Type.STRING,
    "",
    Importance.MEDIUM,
    "Key used to decode AES256 encoded values"
  )
}

case class Aes256ProviderConfig(props: util.Map[String, _])
 extends AbstractConfig(Aes256ProviderConfig.config, props) {
   def aes256Key: String = getString(Aes256ProviderConfig.SECRET_KEY)
 }
