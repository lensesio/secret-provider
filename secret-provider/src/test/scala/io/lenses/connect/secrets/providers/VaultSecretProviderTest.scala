/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.bettercloud.vault.json.JsonArray
import com.bettercloud.vault.json.JsonObject
import io.lenses.connect.secrets.TmpDirUtil.getTempDir
import io.lenses.connect.secrets.TmpDirUtil.separator
import io.lenses.connect.secrets.config.VaultAuthMethod
import io.lenses.connect.secrets.config.VaultProviderConfig
import io.lenses.connect.secrets.config.VaultSettings
import io.lenses.connect.secrets.connect
import io.lenses.connect.secrets.vault.MockVault
import io.lenses.connect.secrets.vault.VaultTestUtils
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.ConfigTransformer
import org.eclipse.jetty.server.Server
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.util.Base64
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Success
import scala.util.Using

class VaultSecretProviderTest extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  val tmp: String = s"$getTempDir${separator}provider-tests-vault"

  val data: JsonObject = new JsonObject().add(
    "data",
    new JsonObject()
      .add("value", "mock")
      .add("another", "value")
      .add("utf8_file_some_key", "secret-value")
      .add(
        "base64_some_key",
        Base64.getEncoder.encodeToString("base64-secret-value".getBytes),
      )
      .add(
        "base64_file_some_key",
        Base64.getEncoder.encodeToString("base64-secret-value".getBytes),
      ),
  )

  val auth: JsonObject = new JsonObject()
    .add("lease_duration", 0)
    .add("policies", new JsonArray())

  val root: JsonObject = new JsonObject()
    .add("data", data)
    .add("auth", auth)
    .add("lease_id", "abcdefg")
    .add("lease_duration", 10000L)
    .add("renewable", true)
  val mockVault = new MockVault(200, root.toString)
  val server: Server = VaultTestUtils.initHttpsMockVault(mockVault)
  val jksFile: String =
    getClass.getClassLoader.getResource("keystore.jks").getPath
  val pemFile:  String = getClass.getClassLoader.getResource("cert.pem").getPath
  val k8sToken: String = getClass.getClassLoader.getResource("cert.pem").getPath

  def cleanUp(): AnyVal = {
    val tmpFile = new File(tmp)
    if (tmpFile.exists) tmpFile.delete()
  }

  override def afterAll(): Unit =
    cleanUp()

  override def beforeAll(): Unit = {
    cleanUp()
    server.start()
  }

  "should renew the token" in {

    val props = Map(
      VaultProviderConfig.VAULT_ADDR       -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN      -> "mock_token",
      VaultProviderConfig.VAULT_PEM        -> pemFile,
      VaultProviderConfig.VAULT_CLIENT_PEM -> pemFile,
      VaultProviderConfig.TOKEN_RENEWAL    -> "1000",
    ).asJava

    val config   = VaultProviderConfig(props)
    val settings = VaultSettings(config)

    settings.pem shouldBe pemFile
    val provider = new VaultSecretProvider()
    provider.configure(props)
    val response = provider.getClient.get.logical.read("secret/hello")
    response.getData.asScala("value") shouldBe "mock"

    Thread.sleep(5000)
    provider.tokenRenewalFailure shouldBe 0
    provider.tokenRenewalSuccess >= 4 shouldBe true
  }

  "should recalculate secret ttl when cached" in {

    // set up secret
    val props = Map(
      VaultProviderConfig.VAULT_ADDR       -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN      -> "mock_token",
      VaultProviderConfig.VAULT_PEM        -> pemFile,
      VaultProviderConfig.VAULT_CLIENT_PEM -> pemFile,
      connect.FILE_DIR                     -> tmp,
    ).asJava

    val config   = VaultProviderConfig(props)
    val settings = VaultSettings(config)

    settings.pem shouldBe pemFile
    val provider = new VaultSecretProvider()
    provider.configure(props)

    val data1 = provider.get("secret/hello")

    Thread.sleep(5000)

    val data2 = provider.get("secret/hello")

    (data1.ttl > data2.ttl) shouldBe true
    (data1.ttl() - data2.ttl()) should be > 5000L
    data2.ttl().longValue() should be < 9995000L
  }

  "should be configured for username and password auth" in {

    val props = Map(
      VaultProviderConfig.VAULT_ADDR  -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN -> "mock_token",
      VaultProviderConfig.VAULT_PEM   -> pemFile,
      VaultProviderConfig.AUTH_METHOD -> VaultAuthMethod.USERPASS.toString,
      VaultProviderConfig.USERNAME    -> "user",
      VaultProviderConfig.PASSWORD    -> "password",
    ).asJava

    val settings = VaultSettings(VaultProviderConfig(props))
    settings.userPass.isDefined shouldBe true
  }

  "should be configured for ldap auth" in {
    val props = Map(
      VaultProviderConfig.VAULT_ADDR       -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN      -> "mock_token",
      VaultProviderConfig.VAULT_PEM        -> pemFile,
      VaultProviderConfig.VAULT_CLIENT_PEM -> pemFile,
      VaultProviderConfig.AUTH_METHOD      -> VaultAuthMethod.LDAP.toString,
      VaultProviderConfig.LDAP_USERNAME    -> "username",
      VaultProviderConfig.LDAP_PASSWORD    -> "password",
    ).asJava

    val settings = VaultSettings(VaultProviderConfig(props))
    settings.ldap.isDefined shouldBe true
  }

  "should be configured for aws auth" in {
    val props = Map(
      VaultProviderConfig.VAULT_ADDR          -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN         -> "mock_token",
      VaultProviderConfig.VAULT_PEM           -> pemFile,
      VaultProviderConfig.AUTH_METHOD         -> VaultAuthMethod.AWSIAM.toString,
      VaultProviderConfig.AWS_REQUEST_BODY    -> "body",
      VaultProviderConfig.AWS_REQUEST_HEADERS -> "headers",
      VaultProviderConfig.AWS_ROLE            -> "role",
      VaultProviderConfig.AWS_REQUEST_URL     -> "url",
    ).asJava

    val settings = VaultSettings(VaultProviderConfig(props))
    settings.awsIam.isDefined shouldBe true
  }

  "should be configured for gcp auth" in {
    val props = Map(
      VaultProviderConfig.VAULT_ADDR  -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN -> "mock_token",
      VaultProviderConfig.VAULT_PEM   -> pemFile,
      VaultProviderConfig.AUTH_METHOD -> VaultAuthMethod.GCP.toString,
      VaultProviderConfig.GCP_ROLE    -> "role",
      VaultProviderConfig.GCP_JWT     -> "jwt",
    ).asJava

    val settings = VaultSettings(VaultProviderConfig(props))
    settings.gcp.isDefined shouldBe true
  }

  "should be configured for jwt auth" in {
    val props = Map(
      VaultProviderConfig.VAULT_ADDR   -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN  -> "mock_token",
      VaultProviderConfig.VAULT_PEM    -> pemFile,
      VaultProviderConfig.AUTH_METHOD  -> VaultAuthMethod.JWT.toString,
      VaultProviderConfig.JWT_PROVIDER -> "provider",
      VaultProviderConfig.JWT_ROLE     -> "role",
      VaultProviderConfig.JWT          -> "jwt",
    ).asJava

    val settings = VaultSettings(VaultProviderConfig(props))
    settings.jwt.isDefined shouldBe true
  }

  "should be configured for github auth" in {
    val props = Map(
      VaultProviderConfig.VAULT_ADDR   -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN  -> "mock_token",
      VaultProviderConfig.VAULT_PEM    -> pemFile,
      VaultProviderConfig.AUTH_METHOD  -> VaultAuthMethod.GITHUB.toString,
      VaultProviderConfig.GITHUB_TOKEN -> "token",
    ).asJava

    val settings = VaultSettings(VaultProviderConfig(props))
    settings.github.isDefined shouldBe true
  }

  "should be configured for kubernetes auth" in {
    val props = Map(
      VaultProviderConfig.VAULT_ADDR            -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN           -> "mock_token",
      VaultProviderConfig.VAULT_PEM             -> pemFile,
      VaultProviderConfig.AUTH_METHOD           -> VaultAuthMethod.KUBERNETES.toString,
      VaultProviderConfig.KUBERNETES_TOKEN_PATH -> k8sToken,
      VaultProviderConfig.KUBERNETES_ROLE       -> "role",
    ).asJava

    val settings = VaultSettings(VaultProviderConfig(props))
    settings.k8s.isDefined shouldBe true
  }

  "should be configured for approle auth" in {
    val props = Map(
      VaultProviderConfig.VAULT_ADDR         -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN        -> "mock_token",
      VaultProviderConfig.VAULT_PEM          -> pemFile,
      VaultProviderConfig.AUTH_METHOD        -> VaultAuthMethod.APPROLE.toString,
      VaultProviderConfig.APP_ROLE           -> "some-app-role",
      VaultProviderConfig.APP_ROLE_SECRET_ID -> "secret",
    ).asJava

    val settings = VaultSettings(VaultProviderConfig(props))
    settings.appRole.isDefined shouldBe true
  }

  "should be configured for ssl with pem" in {
    val props = Map(
      VaultProviderConfig.VAULT_ADDR       -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN      -> "mock_token",
      VaultProviderConfig.VAULT_PEM        -> pemFile,
      VaultProviderConfig.VAULT_CLIENT_PEM -> pemFile,
    ).asJava

    val config   = VaultProviderConfig(props)
    val settings = VaultSettings(config)

    settings.pem shouldBe pemFile
    val provider = new VaultSecretProvider()
    provider.configure(props)
    val response = provider.getClient.get.logical.read("secret/hello")
    response.getData.asScala("value") shouldBe "mock"
  }

  "should be configured for ssl with jks" in {

    val props = Map(
      VaultProviderConfig.VAULT_ADDR           -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN          -> "mock_token",
      VaultProviderConfig.VAULT_KEYSTORE_LOC   -> jksFile,
      VaultProviderConfig.VAULT_TRUSTSTORE_LOC -> jksFile,
      VaultProviderConfig.VAULT_KEYSTORE_PASS  -> "password",
    ).asJava

    val config   = VaultProviderConfig(props)
    val settings = VaultSettings(config)

    settings.keystoreLoc shouldBe jksFile
    settings.keystorePass.value() shouldBe "password"
    val provider = new VaultSecretProvider()
    provider.configure(props)
    val response = provider.getClient.get.logical.read("secret/hello")
    response.getData.asScala("value") shouldBe "mock"
  }

  "should get values at a path" in {

    val props = Map(
      VaultProviderConfig.VAULT_ADDR       -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN      -> "mock_token",
      VaultProviderConfig.VAULT_PEM        -> pemFile,
      VaultProviderConfig.VAULT_CLIENT_PEM -> pemFile,
      connect.FILE_DIR                     -> tmp,
    ).asJava

    val provider = new VaultSecretProvider()
    provider.configure(props)

    val secretKey   = "value"
    val secretValue = "mock"
    val secretPath  = "secret/hello"

    val data: ConfigData = provider.get(secretPath)
    data.data().get(secretKey) shouldBe secretValue
    data.data().get("another") shouldBe "value"

    val data2 = provider.get(secretPath, Set("another").asJava)
    data2.data().containsKey(secretKey) shouldBe false
    data2.data().get("another") shouldBe "value"

    provider.close()
  }

  "should get values at a path for base64" in {

    val props = Map(
      VaultProviderConfig.VAULT_ADDR       -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN      -> "mock_token",
      VaultProviderConfig.VAULT_PEM        -> pemFile,
      VaultProviderConfig.VAULT_CLIENT_PEM -> pemFile,
      connect.FILE_DIR                     -> tmp,
    ).asJava

    val provider = new VaultSecretProvider()
    provider.configure(props)

    val secretKey   = "base64_some_key"
    val secretValue = "base64-secret-value"
    val secretPath  = "secret/hello"

    val data = provider.get(secretPath, Set(secretKey).asJava)
    data.data().get(secretKey) shouldBe secretValue

    provider.close()
  }

  "should get values at a path for base64 file" in {

    val props = Map(
      VaultProviderConfig.VAULT_ADDR       -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN      -> "mock_token",
      VaultProviderConfig.VAULT_PEM        -> pemFile,
      VaultProviderConfig.VAULT_CLIENT_PEM -> pemFile,
      connect.FILE_DIR                     -> tmp,
      connect.WRITE_FILES                  -> true,
    ).asJava

    val provider = new VaultSecretProvider()
    provider.configure(props)

    val secretKey   = "base64_file_some_key"
    val secretValue = "base64-secret-value"
    val secretPath  = "secret/hello"

    val data = provider.get(secretPath, Set(secretKey).asJava)
    val outputFile = data
      .data()
      .get(secretKey)

    outputFile shouldBe s"$tmp$separator$secretPath$separator$secretKey"
    Using(Source.fromFile(outputFile))(_.getLines().mkString) shouldBe Success(
      secretValue,
    )

    provider.close()
  }

  "should get values at a path for utf file" in {

    val props = Map(
      VaultProviderConfig.VAULT_ADDR       -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN      -> "mock_token",
      VaultProviderConfig.VAULT_PEM        -> pemFile,
      VaultProviderConfig.VAULT_CLIENT_PEM -> pemFile,
      connect.FILE_DIR                     -> tmp,
      connect.WRITE_FILES                  -> true,
    ).asJava

    val provider = new VaultSecretProvider()
    provider.configure(props)

    val secretKey   = "utf8_file_some_key"
    val secretValue = "secret-value"
    val secretPath  = "secret/hello"

    val data = provider.get(secretPath, Set(secretKey).asJava)
    val outputFile = data
      .data()
      .get(secretKey)

    outputFile shouldBe s"$tmp$separator$secretPath$separator$secretKey"
    Using(Source.fromFile(outputFile))(_.getLines().mkString) shouldBe Success(
      secretValue,
    )

    provider.close()
  }

  "check transformer" in {

    val props = Map(
      VaultProviderConfig.VAULT_ADDR           -> "https://127.0.0.1:9998",
      VaultProviderConfig.VAULT_TOKEN          -> "mock_token",
      VaultProviderConfig.VAULT_KEYSTORE_LOC   -> jksFile,
      VaultProviderConfig.VAULT_TRUSTSTORE_LOC -> jksFile,
      VaultProviderConfig.VAULT_KEYSTORE_PASS  -> "password",
      connect.FILE_DIR                         -> tmp,
    ).asJava

    val provider = new VaultSecretProvider()
    provider.configure(props)

    // check the workerconfigprovider
    val map = new java.util.HashMap[String, ConfigProvider]()
    map.put("vault", provider)
    val transformer = new ConfigTransformer(map)
    val props2 =
      Map("mykey" -> "${vault:secret/hello:value}").asJava
    val data = transformer.transform(props2)
    data.data().containsKey("value")
    data.data().get("mykey") shouldBe "mock"
  }
}
