/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.azure.core.credential.TokenCredential
import com.azure.security.keyvault.secrets.SecretClient
import com.azure.security.keyvault.secrets.SecretClientBuilder
import io.lenses.connect.secrets.config.AzureProviderConfig
import io.lenses.connect.secrets.config.AzureProviderSettings
import io.lenses.connect.secrets.connect.getSecretsAndExpiry
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider

import java.time.OffsetDateTime
import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class AzureSecretProvider() extends ConfigProvider with AzureHelper {

  private var rootDir:     String                            = _
  private var credentials: Option[TokenCredential]           = None
  val clientMap:           mutable.Map[String, SecretClient] = mutable.Map.empty
  val cache = mutable.Map.empty[String, (Option[OffsetDateTime], ConfigData)]

  // configure the vault client
  override def configure(configs: util.Map[String, _]): Unit = {
    val settings = AzureProviderSettings(AzureProviderConfig(configs))
    rootDir     = settings.fileDir
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
        .buildClient,
    )

    clientMap += (keyVaultUrl -> client)

    val (expiry, data) = cache.get(keyVaultUrl) match {
      case Some((expiresAt, data)) =>
        // we have all the keys and are before the expiry
        val now = OffsetDateTime.now()

        if (
          keys.asScala.subsetOf(data.data().asScala.keySet) && (expiresAt
            .getOrElse(now.plusSeconds(1))
            .isAfter(now))
        ) {
          logger.info("Fetching secrets from cache")
          (
            expiresAt,
            new ConfigData(
              data
                .data()
                .asScala
                .view
                .filter {
                  case (k, _) => keys.contains(k)
                }
                .toMap
                .asJava,
              data.ttl(),
            ),
          )
        } else {
          // missing some or expired so reload
          getSecretsAndExpiry(getSecrets(client, keys.asScala.toSet))
        }

      case None =>
        getSecretsAndExpiry(getSecrets(client, keys.asScala.toSet))
    }

    expiry.foreach(exp => logger.info(s"Min expiry for TTL set to [${exp.toString}]"))
    cache += (keyVaultUrl -> (expiry, data))
    data
  }

  override def close(): Unit = {}

  private def getSecrets(
    client: SecretClient,
    keys:   Set[String],
  ): Map[String, (String, Option[OffsetDateTime])] = {
    val path = client.getVaultUrl.stripPrefix("https://")
    keys.map { key =>
      logger.info(s"Looking up value at [$path] for key [$key]")
      val (value, expiry) = getSecretValue(rootDir, path, client, key)
      (key, (value, expiry))
    }.toMap
  }
}
