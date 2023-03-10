/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import com.amazonaws.services.secretsmanager.AWSSecretsManager
import com.amazonaws.services.secretsmanager.model._
import com.bettercloud.vault.json.JsonObject
import io.lenses.connect.secrets.TmpDirUtil.getTempDir
import io.lenses.connect.secrets.config.AWSProviderConfig
import io.lenses.connect.secrets.config.AWSProviderSettings
import io.lenses.connect.secrets.connect.AuthMode
import io.lenses.connect.secrets.connect.Encoding
import io.lenses.connect.secrets.connect._
import io.lenses.connect.secrets.utils.EncodingAndId
import org.apache.kafka.common.config.ConfigTransformer
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.connect.errors.ConnectException
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import java.nio.file.FileSystems
import java.util.Base64
import java.util.Date
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Success
import scala.util.Using

class AWSSecretProviderTest extends AnyWordSpec with Matchers with MockitoSugar {

  val separator: String = FileSystems.getDefault.getSeparator
  val tmp:       String = s"$getTempDir${separator}provider-tests-aws"

  "should authenticate with credentials" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
    ).asJava

    val provider = new AWSSecretProvider()
    provider.configure(props)
    provider.close()
  }

  "should authenticate with credentials and lookup a secret" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
    ).asJava

    val secretKey   = "my-secret-key"
    val secretName  = "my-secret-name"
    val secretValue = "secret-value"

    val provider = new AWSSecretProvider()
    provider.configure(props)

    val mockClient = mock[AWSSecretsManager]
    val secretValRequest =
      new GetSecretValueRequest().withSecretId(secretName)
    val secretValResponse = new GetSecretValueResult()
    val secretJson        = new JsonObject().add(secretKey, secretValue)
    secretValResponse.setName(secretName)
    secretValResponse.setSecretString(secretJson.toString())

    val now                    = new Date()
    val describeSecretResponse = new DescribeSecretResult()
    describeSecretResponse.setLastRotatedDate(now)
    describeSecretResponse.setRotationEnabled(true)
    describeSecretResponse.setLastRotatedDate(now)

    val rotationRulesType = new RotationRulesType()
    rotationRulesType.setAutomaticallyAfterDays(1.toLong)
    describeSecretResponse.setRotationRules(rotationRulesType)

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    provider.client = Some(mockClient)
    val data = provider.get(secretName, Set(secretKey).asJava)
    data.data().get(secretKey) shouldBe secretValue
    provider.close()
  }

  "should authenticate with credentials and lookup a base64 secret" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
    ).asJava

    val secretKey   = Encoding.BASE64.toString
    val secretName  = "my-secret-name"
    val secretValue = "base64-secret-value"

    val provider = new AWSSecretProvider()
    provider.configure(props)

    val mockClient = mock[AWSSecretsManager]
    val secretValRequest =
      new GetSecretValueRequest().withSecretId(secretName)
    val secretValResponse = new GetSecretValueResult()
    secretValResponse.setName(secretName)
    val secretJson = new JsonObject().add(
      secretKey,
      Base64.getEncoder.encodeToString(secretValue.getBytes),
    )
    secretValResponse.setSecretString(secretJson.toString)

    val now                    = new Date()
    val describeSecretResponse = new DescribeSecretResult()
    describeSecretResponse.setLastRotatedDate(now)
    describeSecretResponse.setRotationEnabled(true)
    describeSecretResponse.setLastRotatedDate(now)

    val rotationRulesType = new RotationRulesType()
    rotationRulesType.setAutomaticallyAfterDays(1.toLong)
    describeSecretResponse.setRotationRules(rotationRulesType)

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    provider.client = Some(mockClient)
    val data = provider.get(secretName, Set(secretKey).asJava)
    data.data().get(secretKey) shouldBe secretValue

    provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should authenticate with credentials and lookup a base64 secret and write to file" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
      FILE_DIR                         -> tmp,
    ).asJava

    val secretKey   = Encoding.BASE64_FILE.toString
    val secretName  = "my-secret-name"
    val secretValue = "base64-secret-value"

    val provider = new AWSSecretProvider()
    provider.configure(props)

    val mockClient = mock[AWSSecretsManager]
    val secretValRequest =
      new GetSecretValueRequest().withSecretId(secretName)
    val secretValResponse = new GetSecretValueResult()
    secretValResponse.setName(secretName)
    val secretJson = new JsonObject().add(
      secretKey,
      Base64.getEncoder.encodeToString("base64-secret-value".getBytes),
    )
    secretValResponse.setSecretString(secretJson.toString)

    val now                    = new Date()
    val describeSecretResponse = new DescribeSecretResult()
    describeSecretResponse.setLastRotatedDate(now)
    describeSecretResponse.setRotationEnabled(true)
    describeSecretResponse.setLastRotatedDate(now)

    val rotationRulesType = new RotationRulesType()
    rotationRulesType.setAutomaticallyAfterDays(1.toLong)
    describeSecretResponse.setRotationRules(rotationRulesType)

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    provider.client = Some(mockClient)
    val data       = provider.get(secretName, Set(secretKey).asJava)
    val outputFile = data.data().get(secretKey)
    outputFile shouldBe s"$tmp$separator$secretName$separator${secretKey.toLowerCase}"

    Using(Source.fromFile(outputFile))(_.getLines().mkString) shouldBe Success(
      secretValue,
    )

    provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should authenticate with credentials and lookup a utf8 secret and write to file" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
      FILE_DIR                         -> tmp,
    ).asJava

    val secretKey =
      s"${Encoding.UTF8_FILE}${EncodingAndId.Separator}my-secret-key"
    val secretName  = "my-secret-name"
    val secretValue = "utf8-secret-value"

    val provider = new AWSSecretProvider()
    provider.configure(props)

    val mockClient = mock[AWSSecretsManager]
    val secretValRequest =
      new GetSecretValueRequest().withSecretId(secretName)
    val secretValResponse = new GetSecretValueResult()
    secretValResponse.setName(secretName)
    val secretJson = new JsonObject().add(
      secretKey,
      secretValue,
    )
    secretValResponse.setSecretString(secretJson.toString)

    val now                    = new Date()
    val describeSecretResponse = new DescribeSecretResult()
    describeSecretResponse.setLastRotatedDate(now)
    describeSecretResponse.setRotationEnabled(true)
    describeSecretResponse.setLastRotatedDate(now)

    val rotationRulesType = new RotationRulesType()
    rotationRulesType.setAutomaticallyAfterDays(1.toLong)
    describeSecretResponse.setRotationRules(rotationRulesType)

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    provider.client = Some(mockClient)
    val data       = provider.get(secretName, Set(secretKey).asJava)
    val outputFile = data.data().get(secretKey)
    outputFile shouldBe s"$tmp$separator$secretName$separator${secretKey.toLowerCase}"

    Using(Source.fromFile(outputFile))(_.getLines().mkString) shouldBe Success(
      secretValue,
    )

    provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should throw an exception if access key not set and not default auth mode" in {

    intercept[ConnectException] {
      AWSProviderSettings(
        AWSProviderConfig(
          Map(
            AWSProviderConfig.AWS_REGION     -> "someregion",
            AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
            AWSProviderConfig.AWS_SECRET_KEY -> "secretId",
          ).asJava,
        ),
      )
    }
  }

  "should throw an exception if secret key not set and not default auth mode" in {

    intercept[ConnectException] {
      AWSProviderSettings(
        AWSProviderConfig(
          Map(
            AWSProviderConfig.AWS_REGION     -> "someregion",
            AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
            AWSProviderConfig.AWS_ACCESS_KEY -> "someclientid",
          ).asJava,
        ),
      )
    }
  }

  "should check transformer" in {

    val secretKey   = s"my-secret-key"
    val secretName  = "my-secret-name"
    val secretValue = "utf8-secret-value"

    val mockClient = mock[AWSSecretsManager]
    val secretValRequest =
      new GetSecretValueRequest().withSecretId(secretName)
    val secretValResponse = new GetSecretValueResult()
    val secretJson        = new JsonObject().add(secretKey, secretValue)
    secretValResponse.setName(secretName)
    secretValResponse.setSecretString(secretJson.toString())

    val now                    = new Date()
    val describeSecretResponse = new DescribeSecretResult()
    describeSecretResponse.setLastRotatedDate(now)
    describeSecretResponse.setRotationEnabled(true)
    describeSecretResponse.setLastRotatedDate(now)

    val rotationRulesType = new RotationRulesType()
    rotationRulesType.setAutomaticallyAfterDays(1.toLong)
    describeSecretResponse.setRotationRules(rotationRulesType)

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
    ).asJava

    val provider = new AWSSecretProvider()
    provider.configure(props)
    provider.client = Some(mockClient)

    // check the workerconfigprovider
    val map = new java.util.HashMap[String, ConfigProvider]()
    map.put("aws", provider)
    val transformer = new ConfigTransformer(map)
    val props2 =
      Map("mykey" -> "${aws:my-secret-name:my-secret-key}").asJava
    val data = transformer.transform(props2)
    data.data().get("mykey") shouldBe secretValue
    provider.close()
  }
}
