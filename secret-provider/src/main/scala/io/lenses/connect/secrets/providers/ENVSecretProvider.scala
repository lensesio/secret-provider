/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import io.lenses.connect.secrets.config.ENVProviderConfig
import io.lenses.connect.secrets.connect.FILE_DIR
import io.lenses.connect.secrets.connect.decode
import io.lenses.connect.secrets.connect.decodeToBytes
import io.lenses.connect.secrets.connect.fileWriter
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.connect.errors.ConnectException

import java.nio.file.FileSystems
import java.util
import scala.jdk.CollectionConverters._

class ENVSecretProvider extends ConfigProvider {

  var vars = Map.empty[String, String]
  var fileDir:           String = ""
  private val separator: String = FileSystems.getDefault.getSeparator
  private val BASE64_FILE = "(ENV-mounted-base64:)(.*$)".r
  private val UTF8_FILE   = "(ENV-mounted:)(.*$)".r
  private val BASE64      = "(ENV-base64:)(.*$)".r

  override def get(path: String): ConfigData =
    new ConfigData(Map.empty[String, String].asJava)

  override def get(path: String, keys: util.Set[String]): ConfigData = {
    val data =
      keys.asScala
        .map { key =>
          val envVarVal =
            vars.getOrElse(
              key,
              throw new ConnectException(
                s"Failed to lookup environment variable [$key]",
              ),
            )

          // match the value to see if its coming from contains
          // the value metadata pattern
          envVarVal match {
            case BASE64_FILE(_, v) =>
              //decode and write to file
              val fileName = s"$fileDir$separator${key.toLowerCase}"
              fileWriter(fileName, decodeToBytes(key, v), key)
              (key, fileName)

            case UTF8_FILE(_, v) =>
              val fileName = s"$fileDir$separator${key.toLowerCase}"
              fileWriter(fileName, v.getBytes(), key)
              (key, fileName)

            case BASE64(_, v) =>
              (key, decode(key, v))

            case _ =>
              (key, envVarVal)
          }
        }
        .toMap
        .asJava

    new ConfigData(data)
  }

  override def configure(configs: util.Map[String, _]): Unit = {
    vars = System.getenv().asScala.toMap
    val config = ENVProviderConfig(configs)
    fileDir = config.getString(FILE_DIR).stripSuffix(separator)
  }

  override def close(): Unit = {}
}
