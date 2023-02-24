package io.lenses.connect.secrets.cache

import com.typesafe.scalalogging.LazyLogging

import java.time.Clock
import scala.collection.concurrent.TrieMap

class TtlCache[V](implicit clock: Clock) extends LazyLogging {

  private val cache = TrieMap[String, ValueWithTtl[V]]()

  private def put(
    key:   String,
    value: ValueWithTtl[V],
  ): ValueWithTtl[V] = {
    cache.put(key, value)
    value
  }

  private def get(
    key:          String,
    fnConditions: Seq[ValueWithTtl[V] => Boolean] = Seq(_ => true),
  ): Option[ValueWithTtl[V]] =
    cache.get(key).filter(value => fnConditions.forall(_(value)))

  def cachingWithTtl(
    key:                String,
    fnCondition:        V => Boolean                       = _ => true,
    fnGetMissingValue:  () => Either[Throwable, ValueWithTtl[V]],
    fnFilterReturnKeys: ValueWithTtl[V] => ValueWithTtl[V] = e => e,
  ): Either[Throwable, ValueWithTtl[V]] =
    getCachedValue(key, fnCondition)
      .orElse(fnGetMissingValue().map(put(key, _)))
      .map(fnFilterReturnKeys)

  private def getCachedValue(key: String, fnCondition: V => Boolean): Either[Throwable, ValueWithTtl[V]] =
    get(
      key,
      Seq(
        v => v.isAlive,
        v => fnCondition(v.value),
      ),
    ).toRight(new RuntimeException("No cached value"))

}
