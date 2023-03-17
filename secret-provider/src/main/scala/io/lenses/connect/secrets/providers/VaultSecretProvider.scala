/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.bettercloud.vault.Vault
import com.bettercloud.vault.response.LogicalResponse
import io.lenses.connect.secrets.async.AsyncFunctionLoop
import io.lenses.connect.secrets.cache.TtlCache
import io.lenses.connect.secrets.cache.ValueWithTtl
import io.lenses.connect.secrets.config.VaultProviderConfig
import io.lenses.connect.secrets.config.VaultSettings
import io.lenses.connect.secrets.connect._
import io.lenses.connect.secrets.utils.ConfigDataBuilder
import io.lenses.connect.secrets.utils.EncodingAndId
import io.lenses.connect.secrets.utils.ExceptionUtils.failWithEx
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.provider.ConfigProvider

import java.time.Clock
import java.util
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.jdk.CollectionConverters.SetHasAsScala
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class VaultSecretProvider() extends ConfigProvider with VaultHelper {

  private var settings:     VaultSettings             = _
  private var vaultClient:  Option[Vault]             = None
  private var tokenRenewal: Option[AsyncFunctionLoop] = None

  private implicit val clock: Clock = Clock.systemDefaultZone()
  private val cache = new TtlCache[Map[String, String]](k => getSecretValue(k))

  def getClient: Option[Vault] = vaultClient

  // configure the vault client
  override def configure(configs: util.Map[String, _]): Unit = {
    settings    = VaultSettings(VaultProviderConfig(configs))
    vaultClient = Some(createClient(settings))
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
    vaultClient.foreach(client => client.auth().renewSelf())

  // lookup secrets at a path
  override def get(path: String): ConfigData = {
    logger.debug(" -> VaultSecretProvider.get(path: {})", path)
    val sec = cache
      .cachingWithTtl(
        path,
      )
      .fold(
        ex => throw ex,
        ConfigDataBuilder(_),
      )
    logger.debug(" <- VaultSecretProvider.get(path: {}, ttl: {})", path, sec.ttl())
    sec
  }

  // get secret keys at a path
  override def get(path: String, keys: util.Set[String]): ConfigData = {
    logger.debug(" -> VaultSecretProvider.get(path: {}, keys: {})", path, keys.asScala)
    val sec = cache.cachingWithTtl(
      path,
      fnCondition        = s => keys.asScala.subsetOf(s.keySet),
      fnFilterReturnKeys = filter(_, keys.asScala.toSet),
    ).fold(
      ex => throw ex,
      ConfigDataBuilder(_),
    )
    logger.debug(" <- VaultSecretProvider.get(path: {}, keys: {}, ttl: {})", path, keys.asScala, sec.ttl())
    sec
  }

  private def filter(
    configData: ValueWithTtl[Map[String, String]],
    keys:       Set[String],
  ): ValueWithTtl[Map[String, String]] =
    configData.copy(value = configData.value.filter { case (k, _) => keys.contains(k) })

  override def close(): Unit =
    tokenRenewal.foreach(_.close())

  // get the secrets and ttl under a path
  val getSecretValue: String => Either[Throwable, ValueWithTtl[Map[String, String]]] = path => {
    logger.debug(s"Looking up value from Vault at [$path]")
    Try(vaultClient.get.logical().read(path)) match {
      case Failure(ex) =>
        failWithEx(s"Failed to fetch secrets from path [$path]", ex)
      case Success(response) if response.getRestResponse.getStatus != 200 =>
        failWithEx(
          s"No secrets found at path [$path]. Vault response: ${new String(response.getRestResponse.getBody)}",
        )
      case Success(response) if response.getData.isEmpty =>
        failWithEx(s"No secrets found at path [$path]")
      case Success(response) =>
        val ttl =
          Option(response.getLeaseDuration).filterNot(_ == 0L).map(Duration(_, TimeUnit.SECONDS))
        Right(
          ValueWithTtl(ttl, settings.defaultTtl, parseSuccessfulResponse(path, response)),
        )
    }
  }

  private def parseSuccessfulResponse(
    path:     String,
    response: LogicalResponse,
  ) = {
    val fileWriterMaybe = settings.fileWriterOpts.map(_.createFileWriter(path))
    response.getData.asScala.map {
      case (k, v) =>
        val encodingAndId = EncodingAndId.from(k)
        val decoded =
          decodeKey(
            encoding = encodingAndId.encoding,
            key      = k,
            value    = v,
            writeFileFn = { content =>
              fileWriterMaybe.fold("nofile")(_.write(k.toLowerCase, content, k).toString)
            },
          )
        (k, decoded)
    }.toMap
  }

}
