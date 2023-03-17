package io.lenses.connect.secrets.test

import com.typesafe.scalalogging.LazyLogging

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import scala.collection.mutable
import scala.jdk.CollectionConverters.MapHasAsScala

object VaultStateValidator extends LazyLogging {

  private case class VaultSecret(created: LocalDateTime, expiry: LocalDateTime, secretValue: String)

  def validateSecret()(implicit vaultState: VaultState) = {

    val vaultSecret = getSecretFromVault()

    val nowInst = LocalDateTime.now()

    logger.info(
      "Secret info (created: {}, expires: {}, now: {})",
      vaultSecret.created,
      vaultSecret.expiry,
      nowInst,
    )

    require(nowInst.isAfter(vaultSecret.created), "secret isn't active yet")
    require(nowInst.isBefore(vaultSecret.expiry), "secret has expired")

  }

  private def getSecretFromVault()(implicit vaultState: VaultState): VaultSecret = {
    val vaultSecret = vaultState.vault.logical().read(vaultState.secretPath)
    val vaultData   = vaultSecret.getData.asScala
    val secretValue = vaultData.getOrElse(
      vaultState.secretKey,
      throw new IllegalStateException("Secret has no value"),
    )
    VaultSecret(
      extractDateFromVaultData(vaultData, "created"),
      extractDateFromVaultData(vaultData, "expires"),
      secretValue,
    )
  }

  private def extractDateFromVaultData(vaultData: mutable.Map[String, String], fieldName: String) = {
    val timeMillis = vaultData
      .get(fieldName)
      .map(java.lang.Long.valueOf)
      .getOrElse(throw new IllegalStateException(s"Secret has no $fieldName time"))

    Instant
      .ofEpochMilli(timeMillis)
      .atZone(ZoneId.systemDefault())
      .toLocalDateTime
  }

}
