package io.lenses.connect.secrets.providers

import io.lenses.connect.secrets.providers.Aes256DecodingHelper.INITIALISATION_VECTOR_SEPARATOR

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import scala.util.Try

object AesDecodingTestHelper {
  private val AES = "AES"

  def encrypt(s: String, key: String): String = {
    val iv = InitializationVector()
    encryptBytes(s.getBytes("UTF-8"), iv, key)
      .map(encrypted =>
        base64Encode(iv.bytes) + INITIALISATION_VECTOR_SEPARATOR + base64Encode(
          encrypted,
        ),
      )
      .get
  }

  private def encryptBytes(
    bytes: Array[Byte],
    iv:    InitializationVector,
    key:   String,
  ): Try[Array[Byte]] =
    for {
      cipher    <- getCipher(Cipher.ENCRYPT_MODE, iv, key)
      encrypted <- Try(cipher.doFinal(bytes))
    } yield encrypted

  private def getCipher(
    mode: Int,
    iv:   InitializationVector,
    key:  String,
  ): Try[Cipher] =
    Try {
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      val ivSpec = new IvParameterSpec(iv.bytes)
      val secret = new SecretKeySpec(key.getBytes("UTF-8"), AES)
      cipher.init(mode, secret, ivSpec)
      cipher
    }

  private def base64Encode(bytes: Array[Byte]) =
    Base64.getEncoder().encodeToString(bytes)
}
