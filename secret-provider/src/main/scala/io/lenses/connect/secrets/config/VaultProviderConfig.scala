/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import io.lenses.connect.secrets.connect.FILE_DIR
import io.lenses.connect.secrets.connect.FILE_DIR_DESC
import io.lenses.connect.secrets.connect.WRITE_FILES
import io.lenses.connect.secrets.connect.WRITE_FILES_DESC
import org.apache.kafka.common.config.ConfigDef.Importance
import org.apache.kafka.common.config.ConfigDef.Type
import org.apache.kafka.common.config.AbstractConfig
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.common.config.SslConfigs

import java.util

object VaultAuthMethod extends Enumeration {
  type VaultAuthMethod = Value
  val KUBERNETES, AWSIAM, GCP, USERPASS, LDAP, JWT, CERT, APPROLE, TOKEN, GITHUB = Value

  def withNameOpt(s: String): Option[Value] = values.find(_.toString == s)
}

object VaultProviderConfig {
  val VAULT_ADDR:       String = "vault.addr"
  val VAULT_TOKEN:      String = "vault.token"
  val VAULT_NAMESPACE:  String = "vault.namespace"
  val VAULT_CLIENT_PEM: String = "vault.client.pem"
  val VAULT_PEM:        String = "vault.pem"
  val VAULT_ENGINE_VERSION = "vault.engine.version"
  val AUTH_METHOD: String = "vault.auth.method"

  val VAULT_TRUSTSTORE_LOC: String =
    s"vault.${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}"
  val VAULT_KEYSTORE_LOC: String =
    s"vault.${SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG}"
  val VAULT_KEYSTORE_PASS: String =
    s"vault.${SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG}"

  val KUBERNETES_ROLE:       String = "kubernetes.role"
  val KUBERNETES_TOKEN_PATH: String = "kubernetes.token.path"
  val KUBERNETES_DEFAULT_TOKEN_PATH: String =
    "/var/run/secrets/kubernetes.io/serviceaccount/token"

  val APP_ROLE:           String = "app.role.id"
  val APP_ROLE_SECRET_ID: String = "app.role.secret.id"

  val AWS_ROLE:            String = "aws.role"
  val AWS_REQUEST_URL:     String = "aws.request.url"
  val AWS_REQUEST_HEADERS: String = "aws.request.headers"
  val AWS_REQUEST_BODY:    String = "aws.request.body"
  val AWS_MOUNT:           String = "aws.mount"

  val GCP_ROLE: String = "gcp.role"
  val GCP_JWT:  String = "gcp.jwt"

  val LDAP_USERNAME: String = "ldap.username"
  val LDAP_PASSWORD: String = "ldap.password"
  val LDAP_MOUNT:    String = "ldap.mount"

  val USERNAME: String = "username"
  val PASSWORD: String = "password"
  val UP_MOUNT: String = "mount"

  val JWT_ROLE:     String = "jwt.role"
  val JWT_PROVIDER: String = "jwt.provider"
  val JWT:          String = "jwt"

  val CERT_MOUNT: String = "cert.mount"

  val GITHUB_TOKEN: String = "github.token"
  val GITHUB_MOUNT: String = "github.mount"

  val TOKEN_RENEWAL:         String = "token.renewal.ms"
  val TOKEN_RENEWAL_DEFAULT: Int    = 600000

  val SECRET_DEFAULT_TTL         = "secret.default.ttl"
  val SECRET_DEFAULT_TTL_DEFAULT = 0L

  val config: ConfigDef = new ConfigDef()
    .define(
      VAULT_ADDR,
      ConfigDef.Type.STRING,
      "http://localhost:8200",
      Importance.HIGH,
      "Address of the Vault server",
    )
    .define(
      VAULT_TOKEN,
      ConfigDef.Type.PASSWORD,
      null,
      Importance.HIGH,
      s"Vault app role token. $AUTH_METHOD must be 'token'",
    )
    .define(
      VAULT_NAMESPACE,
      Type.STRING,
      "",
      Importance.MEDIUM,
      "Sets a global namespace to the Vault server instance. Required Vault Enterprize Pro",
    )
    .define(
      VAULT_KEYSTORE_LOC,
      ConfigDef.Type.STRING,
      "",
      ConfigDef.Importance.HIGH,
      SslConfigs.SSL_KEYSTORE_LOCATION_DOC,
    )
    .define(
      VAULT_KEYSTORE_PASS,
      ConfigDef.Type.PASSWORD,
      "",
      ConfigDef.Importance.HIGH,
      SslConfigs.SSL_KEYSTORE_PASSWORD_DOC,
    )
    .define(
      VAULT_TRUSTSTORE_LOC,
      ConfigDef.Type.STRING,
      "",
      ConfigDef.Importance.HIGH,
      SslConfigs.SSL_TRUSTSTORE_LOCATION_DOC,
    )
    .define(
      VAULT_PEM,
      Type.STRING,
      "",
      Importance.HIGH,
      "File containing the Vault server certificate string contents",
    )
    .define(
      VAULT_CLIENT_PEM,
      Type.STRING,
      "",
      Importance.HIGH,
      "File containing the Client certificate string contents",
    )
    .define(
      VAULT_ENGINE_VERSION,
      Type.INT,
      2,
      Importance.HIGH,
      "KV Secrets Engine version of the Vault server instance. Defaults to 2",
    )
    // auth mode
    .define(
      AUTH_METHOD,
      Type.STRING,
      "token",
      Importance.HIGH,
      """
        |The authentication mode for Vault.
        |Available values are approle, userpass, kubernetes, cert, token, ldap, gcp, awsiam, jwt, github
        |
        |""".stripMargin,
    )
    // app role auth mode
    .define(
      APP_ROLE,
      Type.STRING,
      null,
      Importance.HIGH,
      s"Vault App role id. $AUTH_METHOD must be 'approle'",
    )
    .define(
      APP_ROLE_SECRET_ID,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      s"Vault App role name secret id. $AUTH_METHOD must be 'approle'",
    )
    // userpass
    .define(
      USERNAME,
      Type.STRING,
      null,
      Importance.HIGH,
      s"Username to connect to Vault with. $AUTH_METHOD must be 'userpass'",
    )
    .define(
      PASSWORD,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      s"Password for the username. $AUTH_METHOD must be 'uerspass'",
    )
    .define(
      UP_MOUNT,
      Type.STRING,
      "userpass",
      Importance.HIGH,
      s"The mount name of the userpass authentication back end. Defaults to 'userpass'. $AUTH_METHOD must be 'userpass'",
    )
    // kubernetes
    .define(
      KUBERNETES_ROLE,
      Type.STRING,
      null,
      Importance.HIGH,
      s"The kubernetes role used for authentication. $AUTH_METHOD must be 'kubernetes'",
    )
    .define(
      KUBERNETES_TOKEN_PATH,
      Type.STRING,
      KUBERNETES_DEFAULT_TOKEN_PATH,
      Importance.HIGH,
      s"""
         | s"Path to the service account token. $AUTH_METHOD must be 'kubernetes'.
         | Defaults to $KUBERNETES_DEFAULT_TOKEN_PATH

         | $AUTH_METHOD must be '

         |""".stripMargin,
    )
    // awsiam
    .define(
      AWS_ROLE,
      Type.STRING,
      null,
      Importance.HIGH,
      s"""
         |Name of the role against which the login is being attempted. If role is not specified, t
         in endpoint
         |looks for a role bearing the name of the AMI ID of the EC2 instance that is trying to
         ing the ec2
         |auth method, or the "friendly name" (i.e., role name or username) of the IAM pr
         henticated.
         |If a matching role is not found, login fails. $AUTH_METHOD
 must be 'awsiam'
         |""".stripMargin,
    )
    .define(
      AWS_REQUEST_URL,
      Type.STRING,
      null,
      Importance.HIGH,
      s"""
         |PKCS7 signature of the identity document with all \n characters removed.Base64-encoded Hsed in the signed request.
         |Most likely just aHR0cHM6Ly9zdHMuYW1hem9uYXdzLmNvbS8= (base64-encoding of https://sts.amom/) as most requests will
         |probably use POST with an empty URI. $AUTH_METHOD must be 'awsiam'
         |""".stripMargin,
    )
    .define(
      AWS_REQUEST_HEADERS,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      s"Request headers. $AUTH_METHOD must be 'awsiam'",
    )
    .define(
      AWS_REQUEST_BODY,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      s"""

         ded body of the signed request.
         |Most likely QWN0aW9uPUdldENhbGxlcklkZ
         nNpb249MjAxMS0wNi0xNQ== which is
         |the base64 encoding of Action=GetCallerIdentity&amp;Versi
         5. $AUTH_METHOD must be 'awsiam'
         |""".stripMargin,
    )
    .define(
      AWS_MOUNT,
      Type.STRING,
      "aws",
      Importance.HIGH,
      s"AWS auth mount. $AUTH_METHOD must be 'awsiam'. Default 'aws'",
    )
    //ldap
    .define(
      LDAP_USERNAME,
      Type.STRING,
      null,
      Importance.HIGH,
      s"LDAP username to connect to Vault with. $AUTH_METHOD must be 'ldap'",
    )
    .define(
      LDAP_PASSWORD,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      s"LDAP Password for the username. $AUTH_METHOD must be 'ldap'",
    )
    .define(
      LDAP_MOUNT,
      Type.STRING,
      "ldap",
      Importance.HIGH,
      s"The mount name of the ldap authentication back end. Defaults to 'ldap'. $AUTH_METHOD must be 'ldap'",
    )
    //jwt
    .define(
      JWT_ROLE,
      Type.STRING,
      null,
      Importance.HIGH,
      s"Role the JWT token belongs to. $AUTH_METHOD must be 'jwt'",
    )
    .define(
      JWT_PROVIDER,
      Type.STRING,
      null,
      Importance.HIGH,
      s"Provider of JWT token. $AUTH_METHOD must be 'jwt'",
    )
    .define(
      JWT,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      s"JWT token. $AUTH_METHOD must be 'jwt'",
    )
    //gcp
    .define(
      GCP_ROLE,
      Type.STRING,
      null,
      Importance.HIGH,
      s"The gcp role used for authentication. $AUTH_METHOD must be 'gcp'",
    )
    .define(
      GCP_JWT,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      s"JWT token. $AUTH_METHOD must be 'gcp'",
    )
    // cert mount
    .define(
      CERT_MOUNT,
      Type.STRING,
      "cert",
      Importance.HIGH,
      s"The mount name of the cert authentication back end.  Defaults to 'cert'. $AUTH_METHOD must be 'cert'",
    )
    .define(
      GITHUB_TOKEN,
      Type.PASSWORD,
      null,
      Importance.HIGH,
      s"The github app-id used for authentication. $AUTH_METHOD must be 'github'",
    )
    .define(
      GITHUB_MOUNT,
      Type.STRING,
      "github",
      Importance.HIGH,
      s"The mount name of the github authentication back end.  Defaults to 'cert'. $AUTH_METHOD must be 'github'",
    )
    .define(
      FILE_DIR,
      Type.STRING,
      "",
      Importance.MEDIUM,
      FILE_DIR_DESC,
    )
    .define(
      WRITE_FILES,
      Type.BOOLEAN,
      false,
      Importance.MEDIUM,
      WRITE_FILES_DESC,
    )
    .define(
      TOKEN_RENEWAL,
      Type.INT,
      TOKEN_RENEWAL_DEFAULT,
      Importance.MEDIUM,
      "The time in milliseconds to renew the Vault token",
    )
    .define(
      SECRET_DEFAULT_TTL,
      Type.LONG,
      SECRET_DEFAULT_TTL_DEFAULT,
      Importance.MEDIUM,
      "Default TTL to apply in case a secret has no TTL",
    )

}
case class VaultProviderConfig(props: util.Map[String, _]) extends AbstractConfig(VaultProviderConfig.config, props)
