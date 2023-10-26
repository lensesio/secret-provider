/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import io.lenses.connect.secrets.config.AWSProviderConfig
import io.lenses.connect.secrets.config.AWSProviderSettings
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider
import io.lenses.connect.secrets.providers.AWSHelper._
import java.time.Clock
import java.util
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient

class AWSSecretProvider(testClient: Option[SecretsManagerClient]) extends ConfigProvider {

  def this() = {
    this(Option.empty);
  }

  private implicit val clock: Clock = Clock.systemDefaultZone()

  private var secretProvider: Option[SecretProvider] = None

  override def configure(configs: util.Map[String, _]): Unit = {
    val settings  = AWSProviderSettings(AWSProviderConfig(props = configs))
    val awsClient = testClient.getOrElse(createClient(settings))
    val helper = new AWSHelper(awsClient,
                               settings.defaultTtl,
                               fileWriterCreateFn = () => settings.fileWriterOpts.map(_.createFileWriter()),
                               settings.secretType,
    )
    secretProvider = Some(
      new SecretProvider(
        "AWSSecretProvider",
        helper.lookup,
        helper.close,
      ),
    )
  }

  override def get(path: String): ConfigData =
    secretProvider.fold(throw new IllegalStateException("No client defined"))(_.get(path))

  override def get(path: String, keys: util.Set[String]): ConfigData =
    secretProvider.fold(throw new IllegalStateException("No client defined"))(_.get(path, keys))

  override def close(): Unit = secretProvider.foreach(_.close())

}
