/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import io.github.jopenlibs.vault.SslConfig
import io.github.jopenlibs.vault.Vault
import io.github.jopenlibs.vault.VaultConfig
import io.github.jopenlibs.vault.response.LogicalResponse
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.cache.ValueWithTtl
import io.lenses.connect.secrets.config.VaultAuthMethod
import io.lenses.connect.secrets.config.VaultSettings
import io.lenses.connect.secrets.connect.decodeKey
import io.lenses.connect.secrets.io.FileWriter
import io.lenses.connect.secrets.utils.EncodingAndId
import io.lenses.connect.secrets.utils.ExceptionUtils.failWithEx
import org.apache.kafka.connect.errors.ConnectException

import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.Try

class VaultHelper(
  vaultClient:        Vault,
  defaultTtl:         Option[Duration],
  fileWriterCreateFn: () => Option[FileWriter],
)(
  implicit
  clock: Clock,
) extends SecretHelper
    with LazyLogging {

  override def lookup(path: String): Either[Throwable, ValueWithTtl[Map[String, String]]] = {
    logger.debug(s"Looking up value from Vault at [$path]")
    val lookupFn = if (path.startsWith("database/creds/")) lookupFromDatabaseEngine _ else lookupFromVault _
    lookupFn(path) match {
      case Left(ex) =>
        failWithEx(s"Failed to fetch secrets from path [$path]", ex)
      case Right(response) if response.getRestResponse.getStatus != 200 =>
        failWithEx(
          s"No secrets found at path [$path]. Vault response: ${new String(response.getRestResponse.getBody)}",
        )
      case Right(response) if response.getData.isEmpty =>
        failWithEx(s"No secrets found at path [$path]")
      case Right(response) =>
        val ttl =
          Option(response.getLeaseDuration).filterNot(_ == 0L).map(Duration.of(_, ChronoUnit.SECONDS))
        Right(
          ValueWithTtl(ttl, defaultTtl, parseSuccessfulResponse(response)),
        )
    }
  }

  private def lookupFromVault(path: String): Either[Throwable, LogicalResponse] =
    Try(vaultClient.logical().read(path)).toEither

  private def lookupFromDatabaseEngine(path: String): Either[Throwable, LogicalResponse] = {
    val parts = path.split("/")
    Either.cond(
      parts.length == 3,
      vaultClient.database().creds(parts(2)),
      new ConnectException("Database path is invalid. Path must be in the form 'database/creds/<role_name>'"),
    )
  }

  private def parseSuccessfulResponse(
    response: LogicalResponse,
  ) = {
    val secretValues    = response.getData.asScala
    val fileWriterMaybe = fileWriterCreateFn()
    secretValues.map {
      case (k, v) =>
        (k,
         decodeKey(
           encoding = EncodingAndId.from(k).encoding,
           key      = k,
           value    = v,
           writeFileFn = { content =>
             fileWriterMaybe.fold("nofile")(_.write(k.toLowerCase, content, k).toString)
           },
         ),
        )
    }.toMap
  }
}

object VaultHelper extends StrictLogging {

  // initialize the vault client
  def createClient(settings: VaultSettings): Vault = {
    val config =
      new VaultConfig().address(settings.addr)

    // set ssl if configured
    config.sslConfig(configureSSL(settings))

    if (settings.namespace.nonEmpty) {
      logger.info(s"Setting namespace to ${settings.namespace}")
      config.nameSpace(settings.namespace)
    }

    logger.info(s"Setting engine version to ${settings.engineVersion}")
    config.engineVersion(settings.engineVersion)

    logger.info(s"Setting prefix path depth to ${settings.prefixDepth}")
    config.prefixPathDepth(settings.prefixDepth)

    val vault = new Vault(config.build())

    logger.info(
      s"Initializing client with mode [${settings.authMode.toString}]",
    )

    val token = settings.authMode match {
      case VaultAuthMethod.USERPASS =>
        settings.userPass
          .map(up =>
            vault
              .auth()
              .loginByUserPass(up.username, up.password.value(), up.mount)
              .getAuthClientToken,
          )

      case VaultAuthMethod.APPROLE =>
        settings.appRole
          .map(ar =>
            vault
              .auth()
              .loginByAppRole(ar.path, ar.role, ar.secretId.value())
              .getAuthClientToken,
          )

      case VaultAuthMethod.CERT =>
        settings.cert
          .map(c => vault.auth().loginByCert(c.mount).getAuthClientToken)

      case VaultAuthMethod.AWSIAM =>
        settings.awsIam
          .map(aws =>
            vault
              .auth()
              .loginByAwsIam(
                aws.role,
                aws.url,
                aws.body.value(),
                aws.headers.value(),
                aws.mount,
              )
              .getAuthClientToken,
          )

      case VaultAuthMethod.KUBERNETES =>
        settings.k8s
          .map(k8s =>
            vault
              .auth()
              .loginByJwt("kubernetes", k8s.role, k8s.jwt.value(), k8s.authPath)
              .getAuthClientToken,
          )
      case VaultAuthMethod.GCP =>
        settings.gcp
          .map(gcp =>
            vault
              .auth()
              .loginByGCP(gcp.role, gcp.jwt.value())
              .getAuthClientToken,
          )

      case VaultAuthMethod.LDAP =>
        settings.ldap
          .map(l =>
            vault
              .auth()
              .loginByLDAP(l.username, l.password.value(), l.mount)
              .getAuthClientToken,
          )

      case VaultAuthMethod.JWT =>
        settings.jwt
          .map(j =>
            vault
              .auth()
              .loginByJwt(j.provider, j.role, j.jwt.value())
              .getAuthClientToken,
          )

      case VaultAuthMethod.TOKEN =>
        Some(settings.token.value())

      case VaultAuthMethod.GITHUB =>
        settings.github
          .map(gh =>
            vault
              .auth()
              .loginByGithub(gh.token.value(), gh.mount)
              .getAuthClientToken,
          )

      case _ =>
        throw new ConnectException(
          s"Unsupported auth method [${settings.authMode.toString}]",
        )
    }

    config.token(token.get)
    config.build()
    new Vault(config)
  }

  // set up tls
  private def configureSSL(settings: VaultSettings): SslConfig = {
    val ssl = new SslConfig()

    if (settings.keystoreLoc != "") {
      logger.info(s"Configuring keystore at [${settings.keystoreLoc}]")
      ssl.keyStoreFile(
        new File(settings.keystoreLoc),
        settings.keystorePass.value(),
      )
    }

    if (settings.truststoreLoc != "") {
      logger.info(s"Configuring keystore at [${settings.truststoreLoc}]")
      ssl.trustStoreFile(new File(settings.truststoreLoc))
    }

    if (settings.clientPem != "") {
      logger.info(s"Configuring client PEM. Ignored if JKS set.")
      ssl.clientKeyPemFile(new File(settings.clientPem))
    }

    if (settings.pem != "") {
      logger.info(s"Configuring Vault Server PEM. Ignored if JKS set.")
      ssl.pemFile(new File(settings.pem))
    }

    ssl.build()
  }
}
