package io.lenses.connect.secrets.utils

import io.lenses.connect.secrets.cache.ValueWithTtl
import org.apache.kafka.common.config.ConfigData

import java.time.Clock
import scala.jdk.CollectionConverters.MapHasAsJava

object ConfigDataBuilder {

  def apply(valueWithTtl: ValueWithTtl[Map[String, String]])(implicit clock: Clock) =
    new ConfigData(
      valueWithTtl.value.asJava,
      valueWithTtl.ttlAndExpiry.map(t => Long.box(t.ttlRemaining.toMillis)).orNull,
    )

}
