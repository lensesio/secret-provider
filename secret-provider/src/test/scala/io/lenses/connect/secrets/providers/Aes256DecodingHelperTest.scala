package io.lenses.connect.secrets.providers

import io.lenses.connect.secrets.providers.Aes256DecodingHelper.INITIALISATION_VECTOR_SEPARATOR
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID.randomUUID
import scala.util.Random.nextString
import scala.util.Success

class Aes256DecodingHelperTest extends AnyWordSpec with Matchers with TableDrivenPropertyChecks {

  import AesDecodingTestHelper.encrypt

  "AES-256 decorer" should {
    "not be created for invalid key length" in {
      val secretKey = randomUUID.toString.take(16)
      Aes256DecodingHelper.init(secretKey) shouldBe Symbol("left")
    }

    "not be able to decrypt message for uncrecognized key" in new TestContext {
      forAll(inputs) { text =>
        val otherAes256 = newEncryption(key)

        val encrypted = encrypt(text, generateKey())

        otherAes256.decrypt(encrypted) should not be text
      }
    }

    "decrypt encrypted text" in new TestContext {
      forAll(inputs) { text: String =>
        val aes256    = newEncryption(key)
        val encrypted = encrypt(text, key)

        aes256.decrypt(encrypted) shouldBe Success(text)
      }
    }

    "decrypt same text prefixed with different initialization vector" in new TestContext {
      forAll(inputs) { text: String =>
        val aes256     = newEncryption(key)
        val encrypted1 = encrypt(text, key)
        val encrypted2 = encrypt(text, key)
        removePrefix(encrypted1) should not be removePrefix(encrypted2)

        aes256.decrypt(encrypted1) shouldBe aes256.decrypt(encrypted2)
      }
    }
  }

  trait TestContext {
    val key = generateKey()

    def generateKey(): String = randomUUID.toString.take(32)

    val inputs = Table(
      "string to decode",
      "",
      nextString(length = 1),
      nextString(length = 10),
      nextString(length = 100),
      nextString(length = 1000),
      nextString(length = 10000),
    )

    def removePrefix(s: String) =
      s.split(INITIALISATION_VECTOR_SEPARATOR).tail.head

    def newEncryption(k: String) =
      Aes256DecodingHelper.init(k).fold(m => throw new Exception(m), identity)
  }
}
