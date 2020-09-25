package io.lenses.connect.secrets.providers

import org.apache.kafka.common.config.provider.ConfigProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec
import java.util.Base64
import java.util.UUID.randomUUID
import io.lenses.connect.secrets.config.Aes256ProviderConfig
import scala.collection.JavaConverters._
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.common.config.ConfigTransformer
import io.lenses.connect.secrets.connect.FILE_DIR
import java.io.File
import java.nio.file.Files
import org.scalatest.compatible.Assertion
import scala.io.Source
import java.io.FileInputStream
import scala.util.Random

class Aes256DecodingProviderTest
    extends AnyWordSpec
    with TableDrivenPropertyChecks
    with Matchers {
  import AesDecodingTestHelper.encrypt

  "aes256 provider" should {
    "decrypt aes 256 utf-8 encoded value" in new TestContext with ConfiguredProvider {
      val encrypted = encrypt(value, key)

      forAll(Table("encoding", "", "utf-8")) { encoding =>
        val decrypted =
          provider.get(encoding, Set(encrypted).asJava).data().asScala

        decrypted.get(encrypted) shouldBe Some(value)
      }
    }

    "decrypt aes 256 base64 encoded value" in new TestContext with ConfiguredProvider {
      val encrypted = encrypt(Base64.getEncoder.encodeToString(value.getBytes()), key)

      val decrypted = provider.get("base64", Set(encrypted).asJava).data().asScala

      decrypted.get(encrypted) shouldBe Some(value)
    }

    "decrypt aes 256 encoded value stored in file with utf-8 encoding" in new TestContext
      with ConfiguredProvider {
      val encrypted = encrypt(value, key)

      val providerData = provider.get("utf8_file", Set(encrypted).asJava).data().asScala
      val decryptedPath = providerData.get(encrypted).get

      decryptedPath should startWith(s"$tmpDir/")
      Source.fromFile(decryptedPath).getLines.mkString shouldBe value
    }

    "decrypt aes 256 encoded value stored in file with base64 encoding" in new TestContext
      with ConfiguredProvider {
      val bytesAmount = 100
      val bytesInput = Array.fill[Byte](bytesAmount)(0)
      Random.nextBytes(bytesInput)
      val encrypted = encrypt(Base64.getEncoder.encodeToString(bytesInput), key)

      val providerData = provider.get("base64_file", Set(encrypted).asJava).data().asScala
      val decryptedPath = providerData.get(encrypted).get

      decryptedPath should startWith(s"$tmpDir/")
      val bytesConsumed = Array.fill[Byte](bytesAmount)(0)
      new FileInputStream(decryptedPath).read(bytesConsumed)

      bytesConsumed.toList shouldBe bytesInput.toList
    }

    "transform value referencing to the provider" in new TestContext {
      val value = "hi!"
      val encrypted = encrypt(value, key)
      provider.configure(config)

      val transformer = new ConfigTransformer(
        Map[String, ConfigProvider]("aes256" -> provider).asJava
      )
      val props =
        Map("mykey" -> ("$" + s"{aes256::$encrypted}")).asJava
      val data = transformer.transform(props)
      data.data().containsKey(encrypted)
      data.data().get("mykey") shouldBe value
    }
  }

  forAll(encodings) { encoding =>
    s"aes256 provider for encoding $encoding" should {
      "fail decoding when unable to decode" in new TestContext {
        provider.configure(config)

        assertThrows[ConnectException] {
          provider.get(encoding, Set("abc").asJava)
        }
      }

      "fail decoding when secret key is not configured" in new TestContext {
        assertThrows[ConnectException] {
          provider.get(encoding, Set(encrypt("hi!", key)).asJava)
        }
      }

      "fail decoding when file dir is not configured" in new TestContext {
        assertThrows[ConnectException] {
          provider.get(encoding, Set(encrypt("hi!", key)).asJava)
        }
      }
    }
  }

  "configuring" should {
    "fail for invalid key" in new TestContext {
      assertThrows[ConfigException] {
        provider.configure(
          Map(
            Aes256ProviderConfig.SECRET_KEY -> "too-short",
            FILE_DIR -> "/tmp"
          ).asJava
        )
      }
    }

    "fail for missing secret key" in new TestContext {
      assertThrows[ConfigException] {
        provider.configure(Map(FILE_DIR -> "/tmp").asJava)
      }
    }
  }

  trait ConfiguredProvider {
    self: TestContext =>

    val value = randomUUID().toString()

    provider.configure(config)
  }

  trait TestContext {
    val key = randomUUID.toString.take(32)
    val tmpDir: String = Files
      .createTempDirectory(randomUUID().toString())
      .toFile()
      .getAbsolutePath()
    val provider = new Aes256DecodingProvider()
    val config = Map(
      Aes256ProviderConfig.SECRET_KEY -> key,
      FILE_DIR -> tmpDir
    ).asJava
  }

  private def encodings = Table(
    "encoding",
    "",
    "utf8",
    "utf_file",
    "base64",
    "base64_file"
  )
}
