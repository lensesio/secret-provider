/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.azure.security.keyvault.secrets.SecretClient
import com.azure.security.keyvault.secrets.models.KeyVaultSecret
import com.azure.security.keyvault.secrets.models.SecretProperties
import io.lenses.connect.secrets.TmpDirUtil.getTempDir
import io.lenses.connect.secrets.config.AzureProviderConfig
import io.lenses.connect.secrets.config.AzureProviderSettings
import io.lenses.connect.secrets.connect
import io.lenses.connect.secrets.connect.AuthMode
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.common.config.ConfigTransformer
import org.apache.kafka.connect.errors.ConnectException
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.FileSystems
import java.time.OffsetDateTime
import java.util.Base64
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Success
import scala.util.Using

class AzureSecretProviderTest extends AnyWordSpec with Matchers with MockitoSugar {

  val separator: String = FileSystems.getDefault.getSeparator
  val tmp: String =
    s"$getTempDir${separator}provider-tests-azure"

  "should get secrets at a path with service principal credentials" in {
    val props = Map(
      AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
      AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
      AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
      AzureProviderConfig.AZURE_SECRET_ID -> "somesecretid",
    ).asJava

    val provider = new AzureSecretProvider
    provider.configure(props)

    val secretKey   = "my-key"
    val secretValue = "secret-value"
    val secretPath  = "my-path.vault.azure.net"

    val client = mock[SecretClient]
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")
    val secret           = mock[KeyVaultSecret]
    val secretProperties = mock[SecretProperties]
    val offset           = OffsetDateTime.now()

    // string secret
    when(secretProperties.getExpiresOn).thenReturn(offset)
    when(secret.getValue).thenReturn(secretValue)
    when(secret.getProperties).thenReturn(secretProperties)
    when(client.getSecret(secretKey)).thenReturn(secret)

    // poke in the mocked client
    provider.clientMap += (s"https://$secretPath" -> client)
    val data = provider.get(secretPath, Set(secretKey).asJava)
    data.data().containsKey(secretKey)
    data.data().get(secretKey) shouldBe secretValue

    provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should get base64 secrets at a path with service principal credentials" in {
    val props = Map(
      AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
      AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
      AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
      AzureProviderConfig.AZURE_SECRET_ID -> "somesecretid",
    ).asJava

    val provider = new AzureSecretProvider
    provider.configure(props)

    val secretKey   = "base64-key"
    val secretValue = "base64-secret-value"
    val secretPath  = "my-path.vault.azure.net"

    val client = mock[SecretClient]
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    //base64 secret
    val secretb64           = mock[KeyVaultSecret]
    val secretPropertiesb64 = mock[SecretProperties]
    val ttl                 = OffsetDateTime.now()

    when(secretPropertiesb64.getExpiresOn).thenReturn(ttl)
    when(secretPropertiesb64.getTags)
      .thenReturn(
        Map(connect.FILE_ENCODING -> connect.Encoding.BASE64.toString).asJava,
      )
    when(secretb64.getValue).thenReturn(
      Base64.getEncoder.encodeToString(secretValue.getBytes),
    )
    when(secretb64.getProperties).thenReturn(secretPropertiesb64)
    when(client.getSecret(secretKey)).thenReturn(secretb64)

    // poke in the mocked client
    provider.clientMap += (s"https://$secretPath" -> client)
    val data = provider.get(secretPath, Set(secretKey).asJava)
    data.data().get(secretKey) shouldBe secretValue

    provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should get base64 secrets and write to file" in {
    val props = Map(
      AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
      AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
      AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
      AzureProviderConfig.AZURE_SECRET_ID -> "somesecretid",
      connect.FILE_DIR                    -> tmp,
    ).asJava

    val provider = new AzureSecretProvider
    provider.configure(props)
    val secretKey   = "base64-key"
    val secretValue = "base64-secret-value"
    val secretPath  = "my-path.vault.azure.net"

    val client = mock[SecretClient]
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    //base64 secret
    val secretb64           = mock[KeyVaultSecret]
    val secretPropertiesb64 = mock[SecretProperties]
    val ttl                 = OffsetDateTime.now()

    when(secretPropertiesb64.getExpiresOn).thenReturn(ttl)
    when(secretPropertiesb64.getTags)
      .thenReturn(
        Map(connect.FILE_ENCODING -> connect.Encoding.BASE64_FILE.toString).asJava,
      )
    when(secretb64.getValue).thenReturn(
      Base64.getEncoder.encodeToString(secretValue.getBytes),
    )
    when(secretb64.getProperties).thenReturn(secretPropertiesb64)
    when(client.getSecret(secretKey)).thenReturn(secretb64)
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    // poke in the mocked client
    provider.clientMap += (s"https://$secretPath" -> client)
    val data       = provider.get(secretPath, Set(secretKey).asJava)
    val outputFile = data.data().get(secretKey)

    outputFile shouldBe s"$tmp$separator$secretPath$separator$secretKey"

    Using(Source.fromFile(outputFile))(_.getLines().mkString) shouldBe Success(
      secretValue,
    )

    provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should get utf secrets and write to file" in {
    val props = Map(
      AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
      AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
      AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
      AzureProviderConfig.AZURE_SECRET_ID -> "somesecretid",
      connect.FILE_DIR                    -> tmp,
    ).asJava

    val provider = new AzureSecretProvider
    provider.configure(props)

    val secretKey   = "utf8-key"
    val secretValue = "utf8-secret-value"
    val secretPath  = "my-path.vault.azure.net"

    val client = mock[SecretClient]
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    val secret           = mock[KeyVaultSecret]
    val secretProperties = mock[SecretProperties]
    val ttl              = OffsetDateTime.now()

    when(secretProperties.getExpiresOn).thenReturn(ttl)
    when(secretProperties.getTags)
      .thenReturn(
        Map(connect.FILE_ENCODING -> connect.Encoding.UTF8_FILE.toString).asJava,
      )
    when(secret.getValue).thenReturn(secretValue)
    when(secret.getProperties).thenReturn(secretProperties)
    when(client.getSecret(secretKey)).thenReturn(secret)
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    // poke in the mocked client
    provider.clientMap += (s"https://$secretPath" -> client)
    val data       = provider.get(secretPath, Set(secretKey).asJava)
    val outputFile = data.data().get(secretKey)

    outputFile shouldBe s"$tmp$separator$secretPath$separator$secretKey"

    Using(Source.fromFile(outputFile))(_.getLines().mkString) shouldBe Success(
      secretValue,
    )

    provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should use cache" in {
    val props = Map(
      AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
      AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
      AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
      AzureProviderConfig.AZURE_SECRET_ID -> "somesecretid",
      connect.FILE_DIR                    -> tmp,
    ).asJava

    val provider = new AzureSecretProvider
    provider.configure(props)

    val secretKey  = "utf8-key"
    val secretPath = "my-path.vault.azure.net"

    val client = mock[SecretClient]
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    // poke in the mocked client
    provider.clientMap += (s"https://$secretPath" -> client)
    val now        = OffsetDateTime.now().plusMinutes(10)
    val cachedData = new ConfigData(Map(secretKey -> secretPath).asJava)
    val cached     = (Some(now), cachedData)

    // add to cache
    provider.cache += (s"https://$secretPath" -> cached)
    val data = provider.get(secretPath, Set(secretKey).asJava)
    data.data().containsKey(secretKey)
  }

  "should use not cache because of expiry" in {
    val props = Map(
      AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
      AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
      AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
      AzureProviderConfig.AZURE_SECRET_ID -> "somesecretid",
      connect.FILE_DIR                    -> tmp,
    ).asJava

    val provider = new AzureSecretProvider
    provider.configure(props)

    val secretKey   = "utf8-key"
    val secretValue = "utf8-secret-value"
    val secretPath  = "my-path.vault.azure.net"
    val vaultUrl    = s"https://$secretPath"

    val client = mock[SecretClient]
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    val secret           = mock[KeyVaultSecret]
    val secretProperties = mock[SecretProperties]
    val ttl              = OffsetDateTime.now().plusHours(1)

    when(secretProperties.getExpiresOn).thenReturn(ttl)
    when(secretProperties.getTags)
      .thenReturn(
        Map(connect.FILE_ENCODING -> connect.Encoding.UTF8_FILE.toString).asJava,
      )
    when(secret.getValue).thenReturn(secretValue)
    when(secret.getProperties).thenReturn(secretProperties)
    when(client.getSecret(secretKey)).thenReturn(secret)
    when(client.getVaultUrl).thenReturn(vaultUrl)

    // poke in the mocked client
    provider.clientMap += (vaultUrl -> client)
    //put expiry of cache 1 second behind
    val now        = OffsetDateTime.now().minusSeconds(1)
    val cachedData = new ConfigData(Map(secretKey -> secretPath).asJava)
    val cached     = (Some(now), cachedData)

    // add to cache
    provider.cache += (vaultUrl -> cached)
    val data = provider.get(secretPath, Set(secretKey).asJava)
    data.data().containsKey(secretKey)
    // ttl should be in future now in cache
    provider.cache(vaultUrl)._1.get shouldBe ttl
  }

  "should use not cache because of different keys" in {
    val props = Map(
      AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
      AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
      AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
      AzureProviderConfig.AZURE_SECRET_ID -> "somesecretid",
      connect.FILE_DIR                    -> tmp,
    ).asJava

    val provider = new AzureSecretProvider
    provider.configure(props)

    val secretKey   = "utf8-key"
    val secretValue = "utf8-secret-value"
    val secretPath  = "my-path.vault.azure.net"
    val vaultUrl    = s"https://$secretPath"

    val client = mock[SecretClient]
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    val secret           = mock[KeyVaultSecret]
    val secretProperties = mock[SecretProperties]
    val ttl              = OffsetDateTime.now().plusHours(1)

    when(secretProperties.getExpiresOn).thenReturn(ttl)
    when(secretProperties.getTags)
      .thenReturn(
        Map(connect.FILE_ENCODING -> connect.Encoding.UTF8_FILE.toString).asJava,
      )
    when(secret.getValue).thenReturn(secretValue)
    when(secret.getProperties).thenReturn(secretProperties)
    when(client.getSecret(secretKey)).thenReturn(secret)
    when(client.getVaultUrl).thenReturn(vaultUrl)

    // poke in the mocked client
    provider.clientMap += (vaultUrl -> client)
    //put expiry of cache 1 second behind
    val now        = OffsetDateTime.now()
    val cachedData = new ConfigData(Map("old-key" -> secretPath).asJava)
    val cached     = (Some(now), cachedData)

    // add to cache
    provider.cache += (vaultUrl -> cached)
    val data = provider.get(secretPath, Set(secretKey).asJava)
    data.data().containsKey(secretKey)
  }

  "should throw an exception if client id not set and not default auth mode" in {

    intercept[ConnectException] {
      AzureProviderSettings(
        AzureProviderConfig(
          Map(AzureProviderConfig.AUTH_METHOD -> AuthMode.CREDENTIALS.toString).asJava,
        ),
      )
    }
  }

  "should throw an exception if tenant id not set and not default auth mode" in {

    intercept[ConnectException] {
      AzureProviderSettings(
        AzureProviderConfig(
          Map(
            AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
            AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
          ).asJava,
        ),
      )
    }
  }

  "should throw an exception if secret id not set and not default auth mode" in {

    intercept[ConnectException] {
      AzureProviderSettings(
        AzureProviderConfig(
          Map(
            AzureProviderConfig.AUTH_METHOD     -> AuthMode.CREDENTIALS.toString,
            AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
            AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
          ).asJava,
        ),
      )
    }
  }

  "should not throw an exception if service principals not set and default auth mode" in {

    val settings = AzureProviderSettings(
      AzureProviderConfig(
        Map(AzureProviderConfig.AUTH_METHOD -> AuthMode.DEFAULT.toString).asJava,
      ),
    )

    settings.authMode shouldBe AuthMode.DEFAULT
  }

  "check transformer" in {
    val props1 = Map(
      AzureProviderConfig.AZURE_CLIENT_ID -> "someclientid",
      AzureProviderConfig.AZURE_TENANT_ID -> "sometenantid",
      AzureProviderConfig.AZURE_SECRET_ID -> "somesecretid",
    ).asJava

    val secretKey   = "key-1"
    val secretValue = "utf8-secret-value"
    val secretPath  = "my-path.vault.azure.net"

    val client = mock[SecretClient]
    when(client.getVaultUrl).thenReturn(s"https://$secretPath")

    val secret           = mock[KeyVaultSecret]
    val secretProperties = mock[SecretProperties]
    val offset           = OffsetDateTime.now()

    // string secret
    when(secretProperties.getExpiresOn).thenReturn(offset)
    when(secret.getValue).thenReturn(secretValue)
    when(secret.getProperties).thenReturn(secretProperties)
    when(client.getSecret(secretKey)).thenReturn(secret)

    val provider = new AzureSecretProvider()
    provider.configure(props1)

    provider.clientMap += (s"https://$secretPath" -> client)

    // check the workerconfigprovider
    val map = new java.util.HashMap[String, ConfigProvider]()
    map.put("azure", provider)
    val transformer = new ConfigTransformer(map)
    val props2 =
      Map("mykey" -> "${azure:my-path.vault.azure.net:key-1}").asJava
    val data = transformer.transform(props2)
    data.data().get("mykey") shouldBe secretValue
  }
}
