/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import java.nio.file.FileSystems
import java.util

import com.bettercloud.vault.Vault
import io.lenses.connect.secrets.config.{VaultProviderConfig, VaultSettings}
import io.lenses.connect.secrets.connect._
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.connect.errors.ConnectException

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class VaultSecretProvider() extends ConfigProvider with VaultHelper {

  private val separator: String = FileSystems.getDefault.getSeparator
  private var settings: VaultSettings = _
  private var ttl: Option[Long] = None
  var vaultClient: Option[Vault] = None

  // configure the vault client
  override def configure(configs: util.Map[String, _]): Unit = {
    settings = VaultSettings(VaultProviderConfig(configs))
    val (vault, ttlResponse) = createClient(settings)
    ttl = ttlResponse
    vaultClient = Some(vault)
  }

  def getSecretValues(path: String): Map[String, String] = {
    Try(vaultClient.get.logical().read(path).getData) match {
      case Success(value) =>
        value.asScala.map {
          case (k, v) =>
            val fileName =
              s"${settings.fileDir}$separator$path$separator${k.toLowerCase}"
            val decoded =
              decodeKey(key = k, value = v, fileName = fileName)

            (k, decoded)
        }.toMap

      case Failure(exception) =>
        throw new ConnectException(
          s"Failed to fetch secrets from path [$path]",
          exception
        )
    }
  }

  // lookup secrets at a path
  override def get(path: String): ConfigData = {
    val data = getSecretValues(path).asJava
    ttl.map(t => new ConfigData(data, t)).getOrElse(new ConfigData(data))
  }

  // get secret keys at a path
  override def get(path: String, keys: util.Set[String]): ConfigData = {
    // filter out for the keys we want
    val data = getSecretValues(path).filterKeys(k => keys.contains(k)).asJava

    ttl.map(t => {
      logger.info(s"TTL set to [$t] ms")
      new ConfigData(data, t)
    }).getOrElse(new ConfigData(data))
  }

  override def close(): Unit = {}

}
