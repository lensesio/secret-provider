/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */
package io.lenses.connect.secrets.utils

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration

trait WithRetry {
  protected final def withRetry[T](retry: Int = 5, interval:Option[FiniteDuration])(thunk: => T): T =
    try {
      thunk
    } catch {
      case t: Throwable =>
        if (retry == 0) throw t
        interval match {
          case Some(value) =>
            Thread.sleep(value.toMillis)
            withRetry(retry - 1, interval)(thunk)
          case None =>
            withRetry(retry-1,interval)(thunk)
        }
    }
}
