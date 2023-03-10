/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.amazonaws.services.secretsmanager.AWSSecretsManager
import io.lenses.connect.secrets.config.AWSProviderConfig
import io.lenses.connect.secrets.config.AWSProviderSettings
import io.lenses.connect.secrets.connect.getSecretsAndExpiry
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.connect.errors.ConnectException

import java.time.OffsetDateTime
import java.util
import scala.jdk.CollectionConverters._

class AWSSecretProvider() extends ConfigProvider with AWSHelper {

  var client:  Option[AWSSecretsManager] = None
  var rootDir: String                    = ""

  override def get(path: String): ConfigData =
    new ConfigData(Map.empty[String, String].asJava)

  // path is expected to be the name of the AWS secret
  // keys are expect to be the keys in the payload
  override def get(path: String, keys: util.Set[String]): ConfigData =
    client match {
      case Some(awsClient) =>
        //aws client caches so we don't need to check here
        val (expiry, data) = getSecretsAndExpiry(
          getSecrets(awsClient, path, keys.asScala.toSet),
        )
        expiry.foreach(exp => logger.info(s"Min expiry for TTL set to [${exp.toString}]"))
        data

      case None => throw new ConnectException("AWS client is not set.")
    }

  override def close(): Unit = client.foreach(_.shutdown())

  override def configure(configs: util.Map[String, _]): Unit = {
    val settings = AWSProviderSettings(AWSProviderConfig(props = configs))
    rootDir = settings.fileDir
    client  = Some(createClient(settings))
  }

  def getSecrets(
    awsClient: AWSSecretsManager,
    path:      String,
    keys:      Set[String],
  ): Map[String, (String, Option[OffsetDateTime])] =
    keys.map { key =>
      logger.info(s"Looking up value at [$path] for key [$key]")
      val (value, expiry) = getSecretValue(awsClient, rootDir, path, key)
      (key, (value, expiry))
    }.toMap
}
