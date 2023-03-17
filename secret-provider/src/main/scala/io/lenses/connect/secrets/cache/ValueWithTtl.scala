package io.lenses.connect.secrets.cache

import java.time.Clock
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.Duration
import scala.concurrent.duration.MILLISECONDS

case class Ttl(originalTtl: Duration, expiry: OffsetDateTime) {

  def ttlRemaining(implicit clock: Clock): Duration =
    Duration(expiry.toInstant.toEpochMilli - clock.millis(), MILLISECONDS)

  def isAlive(implicit clock: Clock) = expiry.toInstant.isAfter(clock.instant())

}

object Ttl {
  def apply(ttl: Option[Duration], defaultTtl: Option[Duration])(implicit clock: Clock): Option[Ttl] =
    ttl.orElse(defaultTtl).map(finalTtl => Ttl(finalTtl, ttlToExpiry(finalTtl)))

  def ttlToExpiry(ttl: Duration)(implicit clock: Clock): OffsetDateTime = {
    val offset         = clock.getZone.getRules.getOffset(clock.instant())
    val offsetDateTime = clock.instant().plus(ttl.toMillis, ChronoUnit.MILLIS).atOffset(offset)
    offsetDateTime
  }

}

case class ValueWithTtl[V](ttlAndExpiry: Option[Ttl], value: V) {
  def isAlive(implicit clock: Clock): Boolean = ttlAndExpiry.exists(_.isAlive)
}

object ValueWithTtl {
  def apply[V](ttl: Option[Duration], defaultTtl: Option[Duration], value: V)(implicit clock: Clock): ValueWithTtl[V] =
    ValueWithTtl(Ttl(ttl, defaultTtl), value)
}
