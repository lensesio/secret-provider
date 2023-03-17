package io.lenses.connect.secrets.cache

import com.typesafe.scalalogging.LazyLogging

import java.time.Clock
import scala.collection.concurrent.TrieMap

class TtlCache[V](fnGetMissingValue: String => Either[Throwable, ValueWithTtl[V]])(implicit clock: Clock)
    extends LazyLogging {

  private val cache = TrieMap[String, ValueWithTtl[V]]()

  def cachingWithTtl(
    key:                String,
    fnCondition:        V => Boolean                       = _ => true,
    fnFilterReturnKeys: ValueWithTtl[V] => ValueWithTtl[V] = identity,
  ): Either[Throwable, ValueWithTtl[V]] = {
    get(key,
        Seq(
          v => v.isAlive,
          v => fnCondition(v.value),
        ),
    ).fold(fnGetMissingValue(key).map(put(key, _)))(Right(_))
  }.map(fnFilterReturnKeys)

  private def get(
    key:          String,
    fnConditions: Seq[ValueWithTtl[V] => Boolean] = Seq(_ => true),
  ): Option[ValueWithTtl[V]] =
    cache.get(key).filter(value => fnConditions.forall(_(value)))

  private def put(
    key:   String,
    value: ValueWithTtl[V],
  ): ValueWithTtl[V] = {
    cache.put(key, value)
    value
  }

}
