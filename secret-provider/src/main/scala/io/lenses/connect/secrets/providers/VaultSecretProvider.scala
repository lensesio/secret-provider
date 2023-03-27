/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.bettercloud.vault.Vault
import io.lenses.connect.secrets.async.AsyncFunctionLoop
import io.lenses.connect.secrets.config.VaultProviderConfig
import io.lenses.connect.secrets.config.VaultSettings
import io.lenses.connect.secrets.providers.VaultHelper.createClient
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.connect.errors.ConnectException

import java.time.Clock
import java.util

class VaultSecretProvider() extends ConfigProvider {
  private implicit val clock: Clock = Clock.systemDefaultZone()

  private var maybeVaultClient: Option[Vault]             = None
  private var tokenRenewal:     Option[AsyncFunctionLoop] = None
  private var secretProvider:   Option[SecretProvider]    = None

  def getClient: Option[Vault] = maybeVaultClient

  // configure the vault client
  override def configure(configs: util.Map[String, _]): Unit = {
    val settings    = VaultSettings(VaultProviderConfig(configs))
    val vaultClient = createClient(settings)

    val helper = new VaultHelper(
      vaultClient,
      settings.defaultTtl,
      fileWriterCreateFn = path => settings.fileWriterOpts.map(_.createFileWriter(path)),
    )

    secretProvider   = Some(new SecretProvider(getClass.getSimpleName, helper.lookup))
    maybeVaultClient = Some(vaultClient)
    createRenewalLoop(settings)
  }

  private def createRenewalLoop(settings: VaultSettings): Unit = {
    val renewalLoop = {
      new AsyncFunctionLoop(settings.tokenRenewal, "Vault Token Renewal")(
        renewToken(),
      )
    }
    tokenRenewal = Some(renewalLoop)
    renewalLoop.start()
  }

  def tokenRenewalSuccess: Long = tokenRenewal.map(_.successRate).getOrElse(-1)
  def tokenRenewalFailure: Long = tokenRenewal.map(_.failureRate).getOrElse(-1)

  private def renewToken(): Unit =
    maybeVaultClient.foreach(client => client.auth().renewSelf())

  override def close(): Unit =
    tokenRenewal.foreach(_.close())

  override def get(path: String): ConfigData =
    secretProvider.fold(throw new ConnectException("Vault client is not set."))(_.get(path))

  override def get(path: String, keys: util.Set[String]): ConfigData =
    secretProvider.fold(throw new ConnectException("Vault client is not set."))(_.get(path, keys))
}
