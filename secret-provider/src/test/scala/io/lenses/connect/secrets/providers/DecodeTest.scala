/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import io.lenses.connect.secrets.connect
import io.lenses.connect.secrets.connect.Encoding
import org.apache.commons.io.FileUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.file.FileSystems
import java.time.OffsetDateTime
import java.util.Base64

class DecodeTest extends AnyWordSpec with Matchers {

  val separator: String = FileSystems.getDefault.getSeparator
  val tmp: String =
    System.getProperty("java.io.tmpdir") + separator + "decoder-tests"

  def cleanUp(fileName: String): AnyVal = {
    val tmpFile = new File(fileName)
    if (tmpFile.exists) tmpFile.delete()
  }

  "should decode UTF" in {
    connect.decodeKey(None, "my-key", "secret", _ => fail("No files here")) shouldBe "secret"
    connect.decodeKey(Some(Encoding.UTF8), "my-key", "secret", _ => fail("No files here")) shouldBe "secret"
  }

  "should decode BASE64" in {
    val value = Base64.getEncoder.encodeToString("secret".getBytes)
    connect.decodeKey(Some(Encoding.BASE64), s"my-key", value, _ => fail("No files here")) shouldBe "secret"
  }

  "should decode BASE64 and write to a file" in {
    val fileName = s"${tmp}my-file-base64"

    val value   = Base64.getEncoder.encodeToString("secret".getBytes)
    var written = false
    connect.decodeKey(
      Some(Encoding.BASE64_FILE),
      s"my-key",
      value,
      { _ =>
        written = true
        fileName
      },
    ) shouldBe fileName
    written shouldBe true
  }

  "should decode and write a jks" in {
    val fileName = s"${tmp}my-file-base64-jks"
    val jksFile: String =
      getClass.getClassLoader.getResource("keystore.jks").getPath
    val fileContent = FileUtils.readFileToByteArray(new File(jksFile))
    val jksEncoded  = Base64.getEncoder.encodeToString(fileContent)

    var written = false
    connect.decodeKey(
      Some(Encoding.BASE64_FILE),
      s"keystore.jks",
      jksEncoded,
      { _ =>
        written = true
        fileName
      },
    ) shouldBe fileName

    written shouldBe true
  }

  "should decode UTF8 and write to a file" in {
    val fileName = s"${tmp}my-file-utf8"
    var written  = false

    connect.decodeKey(
      Some(Encoding.UTF8_FILE),
      s"my-key",
      "secret",
      { _ =>
        written = true
        fileName
      },
    ) shouldBe fileName
    written shouldBe true
  }

  "min list test" in {
    val now = OffsetDateTime.now()
    val secrets = Map(
      "ke3"  -> ("value", Some(OffsetDateTime.now().plusHours(3))),
      "key1" -> ("value", Some(now)),
      "key2" -> ("value", Some(OffsetDateTime.now().plusHours(1))),
    )

    val (expiry, _) = connect.getSecretsAndExpiry(secrets)
    expiry shouldBe Some(now)
  }
}
