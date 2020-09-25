package io.lenses.connect.secrets.providers

import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.common.config.ConfigException
import java.util
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import io.lenses.connect.secrets.config.Aes256ProviderConfig
import io.lenses.connect.secrets.connect.{decodeKey, getFileName}
import scala.collection.JavaConverters._
import scala.util.Try
import io.lenses.connect.secrets.connect.Encoding
import java.nio.file.FileSystems
import java.util.UUID.randomUUID

class Aes256DecodingProvider extends ConfigProvider {

  var decoder: Option[Aes256DecodingHelper] = None
  var writeDir: String = ""
  
  override def configure(configs: util.Map[String, _]): Unit = {
    val aes256Cfg = Aes256ProviderConfig(configs)
    val aes256Key = aes256Cfg.aes256Key
    decoder = Option(aes256Key)
      .map(Aes256DecodingHelper.init)
      .map(_.fold(e => throw new ConfigException(e), identity))
    writeDir = aes256Cfg.writeDirectory
  }

  override def get(path: String): ConfigData = new ConfigData(Map.empty[String, String].asJava)
  
  override def get(path: String, keys: util.Set[String]): ConfigData =
    decoder match {
      case Some(d) =>
        def decrypt(key: String): String = {
          val decrypted = d.decrypt(key).fold(e => throw new ConnectException(e.getMessage(), e), identity)
          val keyPrefixedWithEncoding = if (path.nonEmpty) s"${path.toLowerCase}_$key" else key
          decodeKey(
            key = keyPrefixedWithEncoding,
            value = decrypted,
            fileName = getFileName(writeDir, "secrets", randomUUID().toString, FileSystems.getDefault.getSeparator)
          )
        }
          
        new ConfigData(keys.asScala.map(k => k -> decrypt(k)).toMap.asJava)
      case None =>
        throw new ConnectException("decoder is not configured.")
    }

  override def close(): Unit = {}
}
