package io.lenses.connect.secrets.providers

import com.typesafe.scalalogging.LazyLogging
import io.lenses.connect.secrets.cache.TtlCache
import io.lenses.connect.secrets.cache.ValueWithTtl
import io.lenses.connect.secrets.utils.ConfigDataBuilder
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.connect.errors.ConnectException

import java.time.Clock
import java.util
import scala.jdk.CollectionConverters.SetHasAsScala

class SecretProvider(
  providerName:   String,
  getSecretValue: String => Either[Throwable, ValueWithTtl[Map[String, String]]],
  closeFn:        () => Unit = () => (),
)(
  implicit
  clock: Clock,
) extends LazyLogging {

  private val cache = new TtlCache[Map[String, String]](k => getSecretValue(k))

  // lookup secrets at a path
  def get(path: String): ConfigData = {
    logger.debug(" -> {}.get(path: {})", providerName, path)
    val sec = cache
      .cachingWithTtl(
        path,
      )
      .fold(
        ex => throw new ConnectException(ex),
        ConfigDataBuilder(_),
      )
    logger.debug(" <- {}.get(path: {}, ttl: {})", providerName, path, sec.ttl())
    sec
  }

  // get secret keys at a path
  def get(path: String, keys: util.Set[String]): ConfigData = {
    logger.debug(" -> {}.get(path: {}, keys: {})", providerName, path, keys.asScala)
    val sec = cache.cachingWithTtl(
      path,
      fnCondition        = s => keys.asScala.subsetOf(s.keySet),
      fnFilterReturnKeys = filter(_, keys.asScala.toSet),
    ).fold(
      ex => throw new ConnectException(ex),
      ConfigDataBuilder(_),
    )
    logger.debug(" <- {}.get(path: {}, keys: {}, ttl: {})", providerName, path, keys.asScala, sec.ttl())
    sec
  }

  def close(): Unit = closeFn()

  private def filter(
    configData: ValueWithTtl[Map[String, String]],
    keys:       Set[String],
  ): ValueWithTtl[Map[String, String]] =
    configData.copy(value = configData.value.filter { case (k, _) => keys.contains(k) })

}
