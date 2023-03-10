/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */
package io.lenses.connect.secrets.utils

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait WithRetry {

  @tailrec
  protected final def withRetry[T](
    retry:    Int = 5,
    interval: Option[FiniteDuration],
  )(thunk:    => T,
  ): T =
    Try {
      thunk
    } match {
      case Failure(t) =>
        if (retry == 0) throw t
        interval.foreach(sleepValue => Thread.sleep(sleepValue.toMillis))
        withRetry(retry - 1, interval)(thunk)
      case Success(value) => value
    }
}
