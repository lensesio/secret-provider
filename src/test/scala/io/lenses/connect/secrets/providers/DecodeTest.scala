/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import java.io.File
import java.nio.file.FileSystems
import java.util.{Base64, ServiceLoader}

import com.azure.core.http.HttpClientProvider
import io.lenses.connect.secrets.connect
import org.apache.commons.io.FileUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

class DecodeTest extends AnyWordSpec with Matchers {

  val separator: String = FileSystems.getDefault.getSeparator
  val tmp: String =
    System.getProperty("java.io.tmpdir") + separator + "decoder-tests"

  def cleanUp(fileName: String): AnyVal = {
    val tmpFile = new File(fileName)
    if (tmpFile.exists) tmpFile.delete()
  }

  "should decode UTF" in {
    connect.decodeKey("my-key", "secret", "") shouldBe "secret"
  }

  "should decode BASE64" in {
    val value = Base64.getEncoder.encodeToString("secret".getBytes)
    connect.decodeKey(s"${connect.Encoding.BASE64}_-my-key", value, "") shouldBe "secret"
  }

  "should decode BASE64 and write to a file" in {
    val fileName = s"${tmp}my-file-base64"
    cleanUp(fileName)

    val value = Base64.getEncoder.encodeToString("secret".getBytes)
    connect.decodeKey(
      s"${connect.Encoding.BASE64_FILE}_-my-key",
      value,
      fileName
    ) shouldBe fileName
    val result = Source.fromFile(fileName)
    result.getLines().mkString shouldBe "secret"
    result.close()
    cleanUp(fileName)
  }

  "should decode and write a jks" in {
    val fileName = s"${tmp}my-file-base64-jks"
    cleanUp(fileName)

    val jksFile: String =
      getClass.getClassLoader.getResource("keystore.jks").getPath
    val fileContent = FileUtils.readFileToByteArray(new File(jksFile))
    val jksEncoded = Base64.getEncoder.encodeToString(fileContent)

    connect.decodeKey(
      s"${connect.Encoding.BASE64_FILE}_keystore.jks",
      jksEncoded,
      fileName
    ) shouldBe fileName

    val fileContentRes = FileUtils.readFileToByteArray(new File(fileName))
    val jksEncodedRes = Base64.getEncoder.encodeToString(fileContentRes)

    jksEncodedRes shouldBe jksEncoded
    cleanUp(fileName)
  }

  "should decode UTF8 and write to a file" in {
    val fileName = s"${tmp}my-file-utf8"
    cleanUp(fileName)

    connect.decodeKey(
      s"${connect.Encoding.UTF8_FILE}_-my-key",
      "secret",
      fileName
    ) shouldBe fileName
    val result = Source.fromFile(fileName)
    result.getLines().mkString shouldBe "secret"
    result.close()
    cleanUp(fileName)
  }
}
