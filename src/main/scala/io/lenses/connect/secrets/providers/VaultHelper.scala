/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import java.io.File

import com.bettercloud.vault.{SslConfig, Vault, VaultConfig}
import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.config.{VaultAuthMethod, VaultSettings}
import org.apache.kafka.connect.errors.ConnectException

trait VaultHelper extends StrictLogging {

  // initialize the vault client
  def createClient(settings: VaultSettings): (Vault, Option[Long]) = {

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
    val vault = new Vault(config.build())

    logger.info(
      s"Initializing client with mode [${settings.authMode.toString}]"
    )

    val ttl: Option[Long] = settings.authMode match {
      case VaultAuthMethod.USERPASS =>
        settings.userPass
          .map(
            up =>
              vault
                .auth()
                .loginByUserPass(up.username, up.password.value(), up.mount))
          .map(r => r.getAuthLeaseDuration)

      case VaultAuthMethod.APPROLE =>
        settings.appRole
          .map(ar => vault.auth().loginByAppRole(ar.role, ar.secretId.value()))
          .map(r => r.getAuthLeaseDuration)

      case VaultAuthMethod.CERT =>
        settings.cert
          .map(c => vault.auth().loginByCert(c.mount))
          .map(r => r.getAuthLeaseDuration)

      case VaultAuthMethod.AWSIAM =>
        settings.awsIam
          .map(
            aws =>
              vault
                .auth()
                .loginByAwsIam(
                  aws.role,
                  aws.url,
                  aws.body.value(),
                  aws.headers.value(),
                  aws.mount
              ))
          .map(r => r.getAuthLeaseDuration)

      case VaultAuthMethod.KUBERNETES =>
        settings.k8s
          .map(k8s => vault.auth().loginByKubernetes(k8s.role, k8s.jwt.value()))
          .map(r => r.getAuthLeaseDuration)

      case VaultAuthMethod.GCP =>
        settings.gcp
          .map(gcp => vault.auth().loginByGCP(gcp.role, gcp.jwt.value()))
          .map(r => r.getAuthLeaseDuration)

      case VaultAuthMethod.LDAP =>
        settings.ldap
          .map(l =>
            vault.auth().loginByLDAP(l.username, l.password.value(), l.mount))
          .map(r => r.getAuthLeaseDuration)

      case VaultAuthMethod.JWT =>
        settings.jwt
          .map(j => vault.auth().loginByJwt(j.provider, j.role, j.jwt.value()))
          .map(r => r.getAuthLeaseDuration)

      case VaultAuthMethod.TOKEN =>
        config.token(settings.token.value())
        None

      case VaultAuthMethod.GITHUB =>
        settings.github
          .map(gh => vault.auth().loginByGithub(gh.token.value(), gh.mount))
          .map(r => r.getAuthLeaseDuration)

      case _ =>
        throw new ConnectException(
          s"Unsupported auth method [${settings.authMode.toString}]")
    }

    (vault, ttl)
  }

  // set up tls
  private def configureSSL(settings: VaultSettings): SslConfig = {
    val ssl = new SslConfig()

    if (settings.keystoreLoc != "") {
      logger.info(s"Configuring keystore at [${settings.keystoreLoc}]")
      ssl.keyStoreFile(
        new File(settings.keystoreLoc),
        settings.keystorePass.value()
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
