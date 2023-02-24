package io.lenses.connect.secrets.utils

import org.apache.kafka.connect.errors.ConnectException

object ExceptionUtils {

  def failWithEx[T](error: String, ex: Throwable): Either[Throwable, T] =
    failWithEx(error, Some(ex))

  def failWithEx[T](
    error: String,
    ex:    Option[Throwable] = Option.empty,
  ): Either[Throwable, T] =
    Left(
      new ConnectException(error, ex.orNull),
    )
}
