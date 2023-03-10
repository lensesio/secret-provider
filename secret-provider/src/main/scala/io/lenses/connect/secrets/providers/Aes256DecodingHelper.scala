package io.lenses.connect.secrets.providers

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import scala.util.Failure
import scala.util.Try

private[providers] object Aes256DecodingHelper {

  /** characters used to separate initialisation vector from encoded text */
  val INITIALISATION_VECTOR_SEPARATOR = " "

  private val BYTES_AMOUNT = 32
  private val CHARSET      = "UTF-8"

  /**
    * Initializes AES256 decoder for valid key or fails for invalid key
    *
    * @param key key of 32 bytes
    * @return AES256 decoder
    */
  def init(key: String): Either[String, Aes256DecodingHelper] =
    key.getBytes(CHARSET).length match {
      case BYTES_AMOUNT =>
        Right(new Aes256DecodingHelper(key, INITIALISATION_VECTOR_SEPARATOR))
      case n =>
        Left(s"Invalid secret key length ($n - required $BYTES_AMOUNT bytes)")
    }
}

private[providers] class Aes256DecodingHelper private (
  key:         String,
  ivSeparator: String,
) {

  import Aes256DecodingHelper.CHARSET
  import B64._

  private val secret = new SecretKeySpec(key.getBytes(CHARSET), "AES")

  def decrypt(s: String): Try[String] =
    for {
      (iv, encoded) <- InitializationVector.extractInitialisationVector(
        s,
        ivSeparator,
      )
      decoded   <- base64Decode(encoded)
      decrypted <- decryptBytes(iv, decoded)
    } yield new String(decrypted, CHARSET)

  private def decryptBytes(
    iv:    InitializationVector,
    bytes: Array[Byte],
  ): Try[Array[Byte]] =
    for {
      cipher    <- getCipher(Cipher.DECRYPT_MODE, iv)
      encrypted <- Try(cipher.doFinal(bytes))
    } yield encrypted

  private def getCipher(mode: Int, iv: InitializationVector): Try[Cipher] =
    Try {
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      val ivSpec = new IvParameterSpec(iv.bytes)
      cipher.init(mode, secret, ivSpec)
      cipher
    }
}

private case class InitializationVector private (bytes: Array[Byte])

private object InitializationVector {

  import B64._

  private val random = new SecureRandom()
  private val length = 16

  def apply(): InitializationVector = {
    val bytes = Array.fill[Byte](length)(0.byteValue)
    random.nextBytes(bytes)

    new InitializationVector(bytes)
  }

  def extractInitialisationVector(
    s:           String,
    ivSeparator: String,
  ): Try[(InitializationVector, String)] =
    s.indexOf(ivSeparator) match {
      case -1 =>
        Failure(
          new IllegalStateException(
            "Invalid format: missing initialization vector",
          ),
        )
      case i =>
        base64Decode(s.substring(0, i)).map(b => (new InitializationVector(b), s.substring(i + 1)))
    }
}

private object B64 {
  def base64Decode(s: String): Try[Array[Byte]] =
    Try(Base64.getDecoder().decode(s))
}
