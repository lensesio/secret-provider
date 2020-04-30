/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.providers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.JavaConverters._

class ENVSecretProviderTest extends AnyWordSpec with Matchers {

  "should filter and match" in {
    val provider = new ENVSecretProvider()
    provider.vars = Map(
      "RANDOM" -> "somevalue",
      "CONNECT_CASSANDRA_PASSWORD" -> "secret"
    )

    val data = provider.get("", Set("CONNECT_CASSANDRA_PASSWORD").asJava)
    data.data().get("CONNECT_CASSANDRA_PASSWORD") shouldBe "secret"
    data.data().containsKey("RANDOM") shouldBe false

    val data2 = provider.get("", Set("CONNECT_CASSANDRA_PASSWORD", "RANDOM").asJava)
    data2.data().get("CONNECT_CASSANDRA_PASSWORD") shouldBe "secret"
    data2.data().containsKey("RANDOM") shouldBe true
  }
}
