package io.lenses.connect.secrets.cache

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.temporal.ChronoUnit._
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TtlTest extends AnyFunSuite with Matchers with OptionValues {
  implicit val clock = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())

  test("no durations should lead to empty TTL") {
    Ttl(Option.empty, Option.empty) should be(Option.empty)
  }

  test("ttl returned with record should be used") {
    val ttl = Ttl(Some(Duration.of(5, MINUTES)), Option.empty)
    ttl.value.originalTtl should be(Duration.of(5, MINUTES))
    ttl.value.expiry should be(Instant.EPOCH.plus(5, MINUTES).atOffset(toOffset))
  }

  private def toOffset =
    clock.getZone.getRules.getOffset(clock.instant())

  test("default ttl should apply if not available") {
    val ttl = Ttl(Option.empty, Some(Duration.of(5, MINUTES)))
    ttl.value.originalTtl should be(Duration.of(5, MINUTES))
    ttl.value.expiry should be(Instant.EPOCH.plus(5, MINUTES).atOffset(toOffset))
  }

  test("record ttl should be preferred over default") {
    val ttl = Ttl(Some(Duration.of(10, MINUTES)), Some(Duration.of(5, MINUTES)))
    ttl.value.originalTtl should be(Duration.of(10, MINUTES))
    ttl.value.expiry should be(Instant.EPOCH.plus(10, MINUTES).atOffset(toOffset))
  }

}
