/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import java.time.{
  Duration,
  OffsetDateTime
}
import java.util

import com.azure.core.credential.TokenCredential
import com.azure.security.keyvault.secrets.{SecretClient, SecretClientBuilder}
import io.lenses.connect.secrets.config.{
  AzureProviderConfig,
  AzureProviderSettings
}
import io.lenses.connect.secrets.connect.getSecretsAndExpiry
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider

import scala.collection.JavaConverters._
import scala.collection.mutable

class AzureSecretProvider() extends ConfigProvider with AzureHelper {

  private var rootDir: String = _
  private var credentials: Option[TokenCredential] = None
  val clientMap: mutable.Map[String, SecretClient] = mutable.Map.empty
  val cache = mutable.Map.empty[String, (Option[OffsetDateTime], Map[String, String])]

  // configure the vault client
  override def configure(configs: util.Map[String, _]): Unit = {
    val settings = AzureProviderSettings(AzureProviderConfig(configs))
    rootDir = settings.fileDir
    credentials = Some(createCredentials(settings))
  }

  // lookup secrets at a path
  // returns and empty map since Azure is flat
  // and we need to know the secret to lookup
  override def get(path: String): ConfigData =
    new ConfigData(Map.empty[String, String].asJava)

  // get secret keys at a path
  // paths is expected to be the url of the azure keyvault without the protocol (https://)
  // since the connect work will not parse it correctly do to the :
  // including the azure environment.
  override def get(path: String, keys: util.Set[String]): ConfigData = {

    val keyVaultUrl =
      if (path.startsWith("https://")) path else s"https://$path"

    // don't need to cache but allows for testing
    // this way we don't require a keyvault set in the
    // worker properties and we take the path as the target keyvault
    val client = clientMap.getOrElse(
      keyVaultUrl,
      new SecretClientBuilder()
        .vaultUrl(keyVaultUrl)
        .credential(credentials.get)
        .buildClient
    )

    clientMap += (keyVaultUrl -> client)

    val now = OffsetDateTime.now()

    val (expiry, data) = cache.get(keyVaultUrl) match {
      case Some((expiresAt, data)) =>
        // we have all the keys and are before the expiry

        if (keys.asScala.subsetOf(data.keySet) && (expiresAt
              .getOrElse(now.plusSeconds(1))
              .isAfter(now))) {
          logger.info("Fetching secrets from cache")
          (expiresAt,
             data.filterKeys(k => keys.contains(k)))
        } else {
          // missing some or expired so reload
          getSecretsAndExpiry(getSecrets(client, keys.asScala.toSet))
        }

      case None =>
        getSecretsAndExpiry(getSecrets(client, keys.asScala.toSet))
    }

    var ttl = 0L
    expiry.foreach(exp => {
      ttl = Duration.between(now, exp).toMillis
      logger.info(s"Min expiry for TTL set to [${exp.toString}]")
    })
    cache += (keyVaultUrl -> (expiry, data))
    new ConfigData(data.asJava, ttl)
  }

  override def close(): Unit = {}

  private def getSecrets(
      client: SecretClient,
      keys: Set[String]): Map[String, (String, Option[OffsetDateTime])] = {
    val path = client.getVaultUrl.stripPrefix("https://")
    keys.map { key =>
      logger.info(s"Looking up value at [$path] for key [$key]")
      val (value, expiry) = getSecretValue(rootDir, path, client, key)
      (key, (value, expiry))
    }.toMap
  }
}
