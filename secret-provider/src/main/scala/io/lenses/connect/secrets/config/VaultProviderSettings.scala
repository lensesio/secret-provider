/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.config.AbstractConfigExtensions._
import io.lenses.connect.secrets.config.VaultAuthMethod.VaultAuthMethod
import io.lenses.connect.secrets.config.VaultProviderConfig.SECRET_DEFAULT_TTL
import io.lenses.connect.secrets.config.VaultProviderConfig.TOKEN_RENEWAL
import io.lenses.connect.secrets.connect._
import io.lenses.connect.secrets.io.FileWriterOnce
import org.apache.kafka.common.config.types.Password
import org.apache.kafka.connect.errors.ConnectException

import java.nio.file.Paths
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Failure
import scala.util.Success
import scala.util.Using

case class AwsIam(
  role:    String,
  url:     String,
  headers: Password,
  body:    Password,
  mount:   String,
)
case class Gcp(role: String, jwt: Password)
case class Jwt(role: String, provider: String, jwt: Password)
case class UserPass(username: String, password: Password, mount: String)
case class Ldap(username: String, password: Password, mount: String)
case class AppRole(role: String, secretId: Password)
case class K8s(role: String, jwt: Password)
case class Cert(mount: String)
case class Github(token: Password, mount: String)

case class FileWriterOptions(
  fileDir: String,
) {
  def createFileWriter(path: String): FileWriterOnce =
    new FileWriterOnce(Paths.get(fileDir, path))

}

case class VaultSettings(
  addr:           String,
  namespace:      String,
  token:          Password,
  authMode:       VaultAuthMethod,
  keystoreLoc:    String,
  keystorePass:   Password,
  truststoreLoc:  String,
  pem:            String,
  clientPem:      String,
  engineVersion:  Int = 2,
  appRole:        Option[AppRole],
  awsIam:         Option[AwsIam],
  gcp:            Option[Gcp],
  jwt:            Option[Jwt],
  userPass:       Option[UserPass],
  ldap:           Option[Ldap],
  k8s:            Option[K8s],
  cert:           Option[Cert],
  github:         Option[Github],
  tokenRenewal:   FiniteDuration,
  fileWriterOpts: Option[FileWriterOptions],
  defaultTtl:     Option[Duration],
)

object VaultSettings extends StrictLogging {
  def apply(config: VaultProviderConfig): VaultSettings = {
    val addr        = config.getString(VaultProviderConfig.VAULT_ADDR)
    val token       = config.getPassword(VaultProviderConfig.VAULT_TOKEN)
    val namespace   = config.getString(VaultProviderConfig.VAULT_NAMESPACE)
    val keystoreLoc = config.getString(VaultProviderConfig.VAULT_KEYSTORE_LOC)
    val keystorePass =
      config.getPassword(VaultProviderConfig.VAULT_KEYSTORE_PASS)
    val truststoreLoc =
      config.getString(VaultProviderConfig.VAULT_TRUSTSTORE_LOC)
    val pem           = config.getString(VaultProviderConfig.VAULT_PEM)
    val clientPem     = config.getString(VaultProviderConfig.VAULT_CLIENT_PEM)
    val engineVersion = config.getInt(VaultProviderConfig.VAULT_ENGINE_VERSION)

    val authMode = VaultAuthMethod.withNameOpt(
      config.getString(VaultProviderConfig.AUTH_METHOD).toUpperCase,
    ) match {
      case Some(auth) => auth
      case None =>
        throw new ConnectException(
          s"Unsupported ${VaultProviderConfig.AUTH_METHOD}",
        )
    }

    val awsIam =
      if (authMode.equals(VaultAuthMethod.AWSIAM)) Some(getAWS(config))
      else None
    val gcp =
      if (authMode.equals(VaultAuthMethod.GCP)) Some(getGCP(config)) else None
    val appRole =
      if (authMode.equals(VaultAuthMethod.APPROLE)) Some(getAppRole(config))
      else None
    val jwt =
      if (authMode.equals(VaultAuthMethod.JWT)) Some(getJWT(config)) else None
    val k8s =
      if (authMode.equals(VaultAuthMethod.KUBERNETES)) Some(getK8s(config))
      else None
    val userpass =
      if (authMode.equals(VaultAuthMethod.USERPASS)) Some(getUserPass(config))
      else None
    val ldap =
      if (authMode.equals(VaultAuthMethod.LDAP)) Some(getLDAP(config)) else None
    val cert =
      if (authMode.equals(VaultAuthMethod.CERT)) Some(getCert(config)) else None
    val github =
      if (authMode.equals(VaultAuthMethod.GITHUB)) Some(getGitHub(config))
      else None

    val tokenRenewal = config.getInt(TOKEN_RENEWAL).toInt.milliseconds
    VaultSettings(
      addr          = addr,
      namespace     = namespace,
      token         = token,
      authMode      = authMode,
      keystoreLoc   = keystoreLoc,
      keystorePass  = keystorePass,
      truststoreLoc = truststoreLoc,
      pem           = pem,
      clientPem     = clientPem,
      engineVersion = engineVersion,
      appRole       = appRole,
      awsIam        = awsIam,
      gcp           = gcp,
      jwt           = jwt,
      userPass      = userpass,
      ldap          = ldap,
      k8s           = k8s,
      cert          = cert,
      github        = github,
      tokenRenewal  = tokenRenewal,
      fileWriterOpts = Option.when(config.getBoolean(WRITE_FILES)) {
        FileWriterOptions(config.getString(FILE_DIR))
      },
      defaultTtl = Option(config.getLong(SECRET_DEFAULT_TTL).toLong).filterNot(_ == 0L).map(Duration(_, MILLISECONDS)),
    )
  }

  def getCert(config: VaultProviderConfig): Cert =
    Cert(config.getString(VaultProviderConfig.CERT_MOUNT))

  def getGitHub(config: VaultProviderConfig): Github = {
    val token =
      config.getPasswordOrThrowOnNull(VaultProviderConfig.GITHUB_TOKEN)
    val mount = config.getStringOrThrowOnNull(VaultProviderConfig.GITHUB_MOUNT)
    Github(token = token, mount = mount)
  }

  def getAWS(config: VaultProviderConfig): AwsIam = {
    val role = config.getStringOrThrowOnNull(VaultProviderConfig.AWS_ROLE)
    val url  = config.getStringOrThrowOnNull(VaultProviderConfig.AWS_REQUEST_URL)
    val headers =
      config.getPasswordOrThrowOnNull(VaultProviderConfig.AWS_REQUEST_HEADERS)
    val body =
      config.getPasswordOrThrowOnNull(VaultProviderConfig.AWS_REQUEST_BODY)
    val mount = config.getStringOrThrowOnNull(VaultProviderConfig.AWS_MOUNT)
    AwsIam(
      role    = role,
      url     = url,
      headers = headers,
      body    = body,
      mount   = mount,
    )
  }

  def getAppRole(config: VaultProviderConfig): AppRole = {
    val role = config.getStringOrThrowOnNull(VaultProviderConfig.APP_ROLE)
    val secretId =
      config.getPasswordOrThrowOnNull(VaultProviderConfig.APP_ROLE_SECRET_ID)
    AppRole(role = role, secretId = secretId)
  }

  def getK8s(config: VaultProviderConfig): K8s = {
    val role =
      config.getStringOrThrowOnNull(VaultProviderConfig.KUBERNETES_ROLE)
    val path =
      config.getStringOrThrowOnNull(VaultProviderConfig.KUBERNETES_TOKEN_PATH)
    Using(Source.fromFile(path))(_.getLines().mkString) match {
      case Failure(exception) =>
        throw new ConnectException(
          s"Failed to load kubernetes token file [$path]",
          exception,
        )
      case Success(fileContents) =>
        K8s(role = role, jwt = new Password(fileContents))
    }
  }

  def getUserPass(config: VaultProviderConfig): UserPass = {
    val user  = config.getStringOrThrowOnNull(VaultProviderConfig.USERNAME)
    val pass  = config.getPasswordOrThrowOnNull(VaultProviderConfig.PASSWORD)
    val mount = config.getStringOrThrowOnNull(VaultProviderConfig.UP_MOUNT)
    UserPass(username = user, password = pass, mount = mount)
  }

  def getLDAP(config: VaultProviderConfig): Ldap = {
    val user = config.getStringOrThrowOnNull(VaultProviderConfig.LDAP_USERNAME)
    val pass =
      config.getPasswordOrThrowOnNull(VaultProviderConfig.LDAP_PASSWORD)
    val mount = config.getStringOrThrowOnNull(VaultProviderConfig.LDAP_MOUNT)
    Ldap(username = user, password = pass, mount = mount)
  }

  def getGCP(config: VaultProviderConfig): Gcp = {
    val role = config.getStringOrThrowOnNull(VaultProviderConfig.GCP_ROLE)
    val jwt  = config.getPasswordOrThrowOnNull(VaultProviderConfig.GCP_JWT)
    Gcp(role = role, jwt = jwt)
  }

  def getJWT(config: VaultProviderConfig): Jwt = {
    val role = config.getStringOrThrowOnNull(VaultProviderConfig.JWT_ROLE)
    val provider =
      config.getStringOrThrowOnNull(VaultProviderConfig.JWT_PROVIDER)
    val jwt = config.getPasswordOrThrowOnNull(VaultProviderConfig.JWT)
    Jwt(role = role, provider = provider, jwt = jwt)
  }
}
