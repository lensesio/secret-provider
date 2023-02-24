package io.lenses.connect.secrets.test

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters.MapHasAsJava

class TestSinkConnectorTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  test("something to test") {
    val connector = new TestSinkConnector()
    connector.start(Map("a" -> "b").asJava)
  }
}
