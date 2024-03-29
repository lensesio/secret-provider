/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import io.github.jopenlibs.vault.json.JsonObject
import io.lenses.connect.secrets.TmpDirUtil.getTempDir
import io.lenses.connect.secrets.config.AWSProviderConfig
import io.lenses.connect.secrets.config.AWSProviderSettings
import io.lenses.connect.secrets.config.SecretTypeConfig
import io.lenses.connect.secrets.connect._
import io.lenses.connect.secrets.utils.EncodingAndId
import org.apache.kafka.common.config.ConfigTransformer
import org.apache.kafka.common.config.provider.ConfigProvider
import org.apache.kafka.connect.errors.ConnectException
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model._

import java.nio.file.FileSystems
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util
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

    val mockClient = mock[SecretsManagerClient]
    val secretValRequest =
      GetSecretValueRequest.builder().secretId(secretName).build()
    val secretJson        = new JsonObject().add(secretKey, secretValue)
    val secretValResponse = GetSecretValueResponse.builder().name(secretName).secretString(secretJson.toString).build()

    val rotationRulesType = RotationRulesType.builder().automaticallyAfterDays(1L).build()

    val now = new Date()
    val describeSecretResponse = DescribeSecretResponse.builder()
      .lastRotatedDate(now.toInstant)
      .nextRotationDate(now.toInstant.plus(1, ChronoUnit.DAYS))
      .rotationEnabled(true)
      .rotationRules(rotationRulesType)
      .build()

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    val provider = new AWSSecretProvider(testClient = Some(mockClient))
    provider.configure(props)

    val data = provider.get(secretName, Set(secretKey).asJava)
    data.data().get(secretKey) shouldBe secretValue
    provider.close()
  }

  "should authenticate with credentials and lookup a secret without a key" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
      SecretTypeConfig.SECRET_TYPE     -> "string",
    ).asJava

    val secretName  = "my-secret-name"
    val secretValue = "secret-value"

    val mockClient = mock[SecretsManagerClient]
    val secretValRequest =
      GetSecretValueRequest.builder().secretId(secretName).build()
    val secretValResponse = GetSecretValueResponse.builder().name(secretName).secretString(secretValue).build()

    val rotationRulesType = RotationRulesType.builder().automaticallyAfterDays(1L).build()
    val now               = Instant.now()
    val describeSecretResponse = DescribeSecretResponse.builder().lastRotatedDate(now).rotationEnabled(
      true,
    ).lastRotatedDate(now).rotationRules(rotationRulesType).nextRotationDate(now).build()

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    Using.resource(createSecretProvider(props, mockClient)) {
      sp =>
        sp.get(secretName, Set("value").asJava).data().get("value") shouldBe secretValue
        sp.get(secretName).data().get("value") shouldBe secretValue
    }(_.close())
  }

  private def createSecretProvider(props: util.Map[String, String], mockClient: SecretsManagerClient) = {
    val prov = new AWSSecretProvider(Some(mockClient))
    prov.configure(props)
    prov
  }

  "should authenticate with credentials and lookup a base64 secret" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
      WRITE_FILES                      -> true,
      FILE_DIR                         -> "",
    ).asJava

    val secretKey   = Encoding.BASE64.toString
    val secretName  = "my-secret-name"
    val secretValue = "base64-secret-value"

    val mockClient = mock[SecretsManagerClient]
    val secretValRequest =
      GetSecretValueRequest.builder().secretId(secretName).build()

    val secretJson = new JsonObject().add(
      secretKey,
      Base64.getEncoder.encodeToString(secretValue.getBytes),
    )
    val secretValResponse = GetSecretValueResponse.builder()
      .name(secretName)
      .secretString(secretJson.toString)
      .build()

    val rotationRulesType = RotationRulesType.builder().automaticallyAfterDays(1L).build()

    val now = new Date()
    val describeSecretResponse = DescribeSecretResponse.builder().rotationEnabled(true).lastRotatedDate(now.toInstant)
      .nextRotationDate(now.toInstant.plus(1, ChronoUnit.DAYS))
      .rotationRules(rotationRulesType).build()

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)

    when(mockClient.getSecretValue(ArgumentMatchers.eq(secretValRequest)))
      .thenReturn(secretValResponse)
    val provider = new AWSSecretProvider(testClient = Some(mockClient))
    provider.configure(props)

    val data = provider.get(secretName, Set(secretKey).asJava)
    data.data().get(secretKey) shouldBe secretValue

    //provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should authenticate with credentials and lookup a base64 secret and write to file" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
      WRITE_FILES                      -> true,
      FILE_DIR                         -> tmp,
    ).asJava

    val secretKey   = Encoding.BASE64_FILE.toString
    val secretName  = "my-secret-name"
    val secretValue = "base64-secret-value"

    val secretJson = new JsonObject().add(
      secretKey,
      Base64.getEncoder.encodeToString("base64-secret-value".getBytes),
    )

    val mockClient = mock[SecretsManagerClient]
    val secretValRequest =
      GetSecretValueRequest.builder().secretId(secretName).build()
    val secretValResponse = GetSecretValueResponse.builder().name(secretName).secretString(secretJson.toString).build()

    val rotationRulesType = RotationRulesType.builder().automaticallyAfterDays(1L).build()

    val now = new Date()
    val describeSecretResponse =
      DescribeSecretResponse.builder().lastRotatedDate(now.toInstant).nextRotationDate(now.toInstant.plus(
        1,
        ChronoUnit.DAYS,
      ))
        .rotationEnabled(true).rotationRules(rotationRulesType).build()

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    val provider = new AWSSecretProvider(testClient = Some(mockClient))
    provider.configure(props)

    val data       = provider.get(secretName, Set(secretKey).asJava)
    val outputFile = data.data().get(secretKey)
    outputFile shouldBe s"$tmp${separator}secrets$separator${secretKey.toLowerCase}"

    Using(Source.fromFile(outputFile))(_.getLines().mkString) shouldBe Success(
      secretValue,
    )

    //provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should authenticate with credentials and lookup a utf8 secret and write to file" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
      WRITE_FILES                      -> true,
      FILE_DIR                         -> tmp,
    ).asJava

    val secretKey =
      s"${Encoding.UTF8_FILE}${EncodingAndId.Separator}my-secret-key"
    val secretName  = "my-secret-name"
    val secretValue = "utf8-secret-value"

    val secretJson = new JsonObject().add(
      secretKey,
      secretValue,
    )

    val rotationRulesType = RotationRulesType.builder().automaticallyAfterDays(1L).build()

    val mockClient = mock[SecretsManagerClient]
    val secretValRequest =
      GetSecretValueRequest.builder.secretId(secretName).build()
    val secretValResponse = GetSecretValueResponse.builder().name(secretName).secretString(secretJson.toString).build()

    val now = new Date()
    val describeSecretResponse =
      DescribeSecretResponse.builder().lastRotatedDate(now.toInstant).nextRotationDate(now.toInstant.plus(
        1,
        ChronoUnit.DAYS,
      ))
        .rotationEnabled(true).rotationRules(rotationRulesType).build()

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    val provider = new AWSSecretProvider(testClient = Some(mockClient))
    provider.configure(props)

    val data       = provider.get(secretName, Set(secretKey).asJava)
    val outputFile = data.data().get(secretKey)
    outputFile shouldBe s"$tmp${separator}secrets$separator${secretKey.toLowerCase}"

    Using(Source.fromFile(outputFile))(_.getLines().mkString) shouldBe Success(
      secretValue,
    )

    //provider.get("").data().isEmpty shouldBe true
    provider.close()
  }

  "should allow default auth mode" in {

    val provSettings = AWSProviderSettings(
      AWSProviderConfig(
        Map(
          AWSProviderConfig.AWS_REGION  -> "someregion",
          AWSProviderConfig.AUTH_METHOD -> AuthMode.DEFAULT.toString,
        ).asJava,
      ),
    )

    provSettings.authMode should be(AuthMode.DEFAULT)
    provSettings.credentials should be(empty)
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

    val mockClient = mock[SecretsManagerClient]
    val secretValRequest =
      GetSecretValueRequest.builder().secretId(secretName).build()

    val secretJson        = new JsonObject().add(secretKey, secretValue)
    val secretValResponse = GetSecretValueResponse.builder().name(secretName).secretString(secretJson.toString).build()

    val rotationRulesType = RotationRulesType.builder().automaticallyAfterDays(1L).build()

    val now = new Date()
    val describeSecretResponse =
      DescribeSecretResponse.builder().lastRotatedDate(now.toInstant).nextRotationDate(now.toInstant.plus(
        1,
        ChronoUnit.DAYS,
      ))
        .rotationEnabled(true).rotationRules(rotationRulesType).build()

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

    val provider = new AWSSecretProvider(testClient = Some(mockClient))
    provider.configure(props)

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

  "should not throw exception with secret value in message" in {
    val props = Map(
      AWSProviderConfig.AUTH_METHOD    -> AuthMode.CREDENTIALS.toString,
      AWSProviderConfig.AWS_ACCESS_KEY -> "somekey",
      AWSProviderConfig.AWS_SECRET_KEY -> "secretkey",
      AWSProviderConfig.AWS_REGION     -> "someregion",
    ).asJava

    val secretKey   = "my-secret-key"
    val secretName  = "my-secret-name"
    val secretValue = "secret-value"

    val mockClient = mock[SecretsManagerClient]
    val secretValRequest =
      GetSecretValueRequest.builder().secretId(secretName).build()

    // Not json, just the string - so should cause json parse exception
    val secretValResponse = GetSecretValueResponse.builder().name(secretName).secretString(secretValue).build()
    val now               = Instant.now()
    val rotationRulesType = RotationRulesType.builder().automaticallyAfterDays(1L).build()

    val describeSecretResponse = DescribeSecretResponse.builder().lastRotatedDate(now).rotationEnabled(
      true,
    ).lastRotatedDate(now).rotationRules(rotationRulesType).nextRotationDate(now).build()

    when(mockClient.describeSecret(any[DescribeSecretRequest]))
      .thenReturn(describeSecretResponse)
    when(mockClient.getSecretValue(secretValRequest))
      .thenReturn(secretValResponse)

    Using.resource(createSecretProvider(props, mockClient)) {
      sp =>
        intercept[Exception] {
          sp.get(secretName, Set(secretKey).asJava)
        }.getMessage should not include secretValue
    }(_.close())

  }
}
