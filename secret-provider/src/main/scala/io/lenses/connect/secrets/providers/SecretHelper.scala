package io.lenses.connect.secrets.providers

import io.lenses.connect.secrets.cache.ValueWithTtl

trait SecretHelper {

  def lookup(path: String): Either[Throwable, ValueWithTtl[Map[String, String]]]

  def close(): Unit = ()
}
