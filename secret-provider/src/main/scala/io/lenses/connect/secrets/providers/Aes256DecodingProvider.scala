package io.lenses.connect.secrets.providers

import io.lenses.connect.secrets.config.Aes256ProviderConfig
import io.lenses.connect.secrets.connect.decodeKey
import io.lenses.connect.secrets.io.FileWriter
import io.lenses.connect.secrets.io.FileWriterOnce
import io.lenses.connect.secrets.utils.EncodingAndId
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.connect.errors.ConnectException

import java.nio.file.Paths
import java.util
import scala.jdk.CollectionConverters._

class Aes256DecodingProvider extends ConfigProvider {

  var decoder: Option[Aes256DecodingHelper] = None

  private var fileWriter: FileWriter = _

  override def configure(configs: util.Map[String, _]): Unit = {
    val aes256Cfg = Aes256ProviderConfig(configs)
    val aes256Key = aes256Cfg.aes256Key
    val writeDir  = aes256Cfg.writeDirectory

    decoder = Option(aes256Key)
      .map(Aes256DecodingHelper.init)
      .map(_.fold(e => throw new ConfigException(e), identity))
    fileWriter = new FileWriterOnce(Paths.get(writeDir, "secrets"))
  }

  override def get(path: String): ConfigData =
    new ConfigData(Map.empty[String, String].asJava)

  override def get(path: String, keys: util.Set[String]): ConfigData = {
    val encodingAndId = EncodingAndId.from(path)
    decoder match {
      case Some(d) =>
        def decrypt(key: String): String = {
          val decrypted = d
            .decrypt(key)
            .fold(
              e => throw new ConnectException("Failed to decrypt the secret.", e),
              identity,
            )
          decodeKey(
            key      = key,
            value    = decrypted,
            encoding = encodingAndId.encoding,
            writeFileFn = { content =>
              encodingAndId.id match {
                case Some(value) =>
                  fileWriter.write(value, content, key).toString
                case None =>
                  throw new ConnectException(
                    s"Invalid argument received for key:$key. Expecting a file identifier.",
                  )
              }
            },
          )
        }

        new ConfigData(keys.asScala.map(k => k -> decrypt(k)).toMap.asJava)
      case None =>
        throw new ConnectException("decoder is not configured.")
    }
  }

  override def close(): Unit = {}
}
