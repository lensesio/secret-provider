/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import java.nio.file.FileSystems
import java.time.OffsetDateTime
import java.{io, lang, util}

import com.bettercloud.vault.Vault
import _root_.io.lenses.connect.secrets.config.{
  VaultProviderConfig,
  VaultSettings
}
import _root_.io.lenses.connect.secrets.connect._
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.connect.errors.ConnectException

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class VaultSecretProvider() extends ConfigProvider with VaultHelper {

  private val separator: String = FileSystems.getDefault.getSeparator
  private var settings: VaultSettings = _
  var vaultClient: Option[Vault] = None
  val cache = mutable.Map.empty[String, (Option[OffsetDateTime], ConfigData)]

  // configure the vault client
  override def configure(configs: util.Map[String, _]): Unit = {
    settings = VaultSettings(VaultProviderConfig(configs))
    vaultClient = Some(createClient(settings))
  }

  // lookup secrets at a path
  override def get(path: String): ConfigData = {
    val (expiry, data) = cache.get(path) match {
      case Some((expiresAt, data)) =>
        // we have all the keys and are before the expiry
        val now = OffsetDateTime.now()

        if (expiresAt.getOrElse(now.plusSeconds(1)).isAfter(now)) {
          logger.info("Fetching secrets from cache")
          (expiresAt, data)
        } else {
          // missing some or expired so reload
          getSecretsAndExpiry(getSecrets(path))
        }

      case None =>
        getSecretsAndExpiry(getSecrets(path))
    }

    expiry.foreach(exp =>
      logger.info(s"Min expiry for TTL set to [${exp.toString}]"))
    cache += (path -> (expiry, data))
    data
  }

  // get secret keys at a path
  override def get(path: String, keys: util.Set[String]): ConfigData = {

    val (expiry, data) = cache.get(path) match {
      case Some((expiresAt, data)) =>
        // we have all the keys and are before the expiry
        val now = OffsetDateTime.now()

        if (keys.asScala.subsetOf(data.data().asScala.keySet) && (expiresAt
              .getOrElse(now.plusSeconds(1))
              .isAfter(now))) {
          logger.info("Fetching secrets from cache")
          (expiresAt,
           new ConfigData(
             data.data().asScala.filterKeys(k => keys.contains(k)).asJava,
             data.ttl()))
        } else {
          // missing some or expired so reload
          getSecretsAndExpiry(
            getSecrets(path).filterKeys(k => keys.contains(k)))
        }

      case None =>
        getSecretsAndExpiry(getSecrets(path).filterKeys(k => keys.contains(k)))
    }

    expiry.foreach(exp =>
      logger.info(s"Min expiry for TTL set to [${exp.toString}]"))
    cache += (path -> (expiry, data))
    data
  }

  override def close(): Unit = {}

  // get the secrets and ttl under a path
  def getSecrets(
      path: String): Map[String, (String, Option[OffsetDateTime])] = {
    val now = OffsetDateTime.now()

    logger.info(s"Looking up value at [$path]")

    Try(vaultClient.get.logical().read(path)) match {
      case Success(response) =>
        if (response.getRestResponse.getStatus != 200) {
          throw new ConnectException(
            s"No secrets found at path [$path]. Vault response: ${new String(
              response.getRestResponse.getBody)}"
          )
        }

        val ttl = Option(vaultClient.get.logical().read(path).getLeaseDuration) match {
          case Some(duration) => Some(now.plusSeconds(duration))
          case None           => None
        }

        if (response.getData.isEmpty) {
          throw new ConnectException(
            s"No secrets found at path [$path]"
          )
        }

        response.getData.asScala.map {
          case (k, v) =>
            val fileName =
              s"${settings.fileDir}$separator$path$separator${k.toLowerCase}"
            val decoded =
              decodeKey(key = k, value = v, fileName = fileName)

            (k, (decoded, ttl))
        }.toMap

      case Failure(exception) =>
        throw new ConnectException(
          s"Failed to fetch secrets from path [$path]",
          exception
        )
    }
  }
}
