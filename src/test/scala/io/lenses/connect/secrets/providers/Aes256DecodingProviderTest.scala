package io.lenses.connect.secrets.providers

import org.apache.kafka.common.config.provider.ConfigProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.util.UUID.randomUUID
import io.lenses.connect.secrets.config.Aes256ProviderConfig
import scala.collection.JavaConverters._
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.common.config.ConfigTransformer

class Aes256DecodingProviderTest extends AnyWordSpec with Matchers {
  import AesDecodingTestHelper.encrypt

  "aes256 provider" should {
    "decode aes 256 encoded value" in new TestContext {
      val value = "hello!!"
      val encrypted = encrypt(value, key)
      provider.configure(config)

      val decrypted = provider.get("", Set(encrypted).asJava).data().asScala
      
      decrypted.get(encrypted) shouldBe Some(value)
    }

    "fail decoding when unable to decode" in new TestContext {
      provider.configure(config)
      
      assertThrows[ConnectException] {
        provider.get("", Set("abc").asJava)
      }
    }

    "fail decoding when not configured" in new TestContext {
      assertThrows[ConnectException] {
        provider.get("", Set(encrypt("hi!", key)).asJava)
      }
    }

    "fail to configure with invalid key" in new TestContext {
      assertThrows[ConfigException] {
        provider.configure(Map(Aes256ProviderConfig.SECRET_KEY -> "too-short").asJava)
      }
    }

    "transform value referencin to the provider" in new TestContext {
      val value = "hi!"
      val encrypted = encrypt(value, key)
      provider.configure(config)

      val transformer = new ConfigTransformer(Map[String, ConfigProvider]("aes256" -> provider).asJava)
      val props = Map("mykey" -> ("$" + s"{aes256::$encrypted}")).asJava
      val data = transformer.transform(props)
      data.data().containsKey(encrypted)
      data.data().get("mykey") shouldBe value
    }
  }
  
  trait TestContext {
    val key = randomUUID.toString.take(32)
    val provider = new Aes256DecodingProvider()
    val config = Map(
      Aes256ProviderConfig.SECRET_KEY -> key
    ).asJava
  }
}
