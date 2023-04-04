package io.lenses.connect.secrets.utils

import io.lenses.connect.secrets.cache.ValueWithTtl
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit._
class ConfigDataBuilderTest extends AnyFunSuite with Matchers {

  implicit val clock = Clock.systemDefaultZone()

  test("Converts to the expected java structure") {
    val map = ValueWithTtl(
      Option(Duration.of(1, MINUTES)),
      Option.empty[Duration],
      Map[String, String](
        "secret" -> "12345",
      ),
    )
    val ret = ConfigDataBuilder(map)
    ret.ttl().longValue() should be > 0L
    ret.data().get("secret") should be("12345")
  }
}
