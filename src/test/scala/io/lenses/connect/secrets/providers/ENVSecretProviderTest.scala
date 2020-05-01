/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import java.nio.file.FileSystems
import java.util.Base64

import org.apache.kafka.common.config.ConfigTransformer
import org.apache.kafka.common.config.provider.ConfigProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.JavaConverters._
import scala.io.Source

class ENVSecretProviderTest extends AnyWordSpec with Matchers {

  val separator: String = FileSystems.getDefault.getSeparator
  val tmp: String =
    System.getProperty("java.io.tmpdir").stripSuffix(separator) + separator + "provider-tests-env"

  "should filter and match" in {
    val provider = new ENVSecretProvider()
    provider.vars = Map(
      "RANDOM" -> "somevalue",
      "CONNECT_CASSANDRA_PASSWORD" -> "secret",
      "BASE64" -> s"ENV-base64:${Base64.getEncoder.encodeToString("my-base64-secret".getBytes)}",
      "BASE64_FILE" -> s"ENV-mounted-base64:${Base64.getEncoder.encodeToString("my-base64-secret".getBytes)}",
      "UTF8_FILE" -> s"ENV-mounted:my-secret"
    )
    provider.fileDir = tmp

    val data = provider.get("", Set("CONNECT_CASSANDRA_PASSWORD").asJava)
    data.data().get("CONNECT_CASSANDRA_PASSWORD") shouldBe "secret"
    data.data().containsKey("RANDOM") shouldBe false

    val data2 = provider.get("", Set("CONNECT_CASSANDRA_PASSWORD", "RANDOM").asJava)
    data2.data().get("CONNECT_CASSANDRA_PASSWORD") shouldBe "secret"
    data2.data().containsKey("RANDOM") shouldBe true

    val data3 = provider.get("", Set("BASE64").asJava)
    data3.data().get("BASE64") shouldBe "my-base64-secret"

    val data4 = provider.get("", Set("BASE64_FILE").asJava)
    val outputFile = data4.data().get("BASE64_FILE")
    outputFile shouldBe s"$tmp${separator}base64_file"

    val result = Source.fromFile(outputFile)
    result.getLines().mkString shouldBe "my-base64-secret"
    result.close()

    val data5 = provider.get("", Set("UTF8_FILE").asJava)
    val outputFile5 = data5.data().get("UTF8_FILE")
    outputFile5 shouldBe s"$tmp${separator}utf8_file"

    val result2 = Source.fromFile(outputFile5)
    result2.getLines().mkString shouldBe "my-secret"
    result2.close()

  }

  "check transformer" in {

    val props = Map.empty[String, String].asJava

    val provider = new ENVSecretProvider()
    provider.vars = Map("CONNECT_PASSWORD" -> "secret")

    // check the workerconfigprovider
    val map = new java.util.HashMap[String, ConfigProvider]()
    map.put("env", provider)
    val transformer = new ConfigTransformer(map)
    val props2 =
      Map("mykey" -> "${env::CONNECT_PASSWORD}").asJava
    val data = transformer.transform(props2)
    data.data().containsKey("value")
    data.data().get("mykey") shouldBe "secret"
  }
}
