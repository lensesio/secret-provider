/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.async

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.Duration

class AsyncFunctionLoop(interval: Duration, description: String)(thunk: => Unit)
  extends AutoCloseable
    with StrictLogging {

  private val running = new AtomicBoolean(false)
  private val executorService = Executors.newFixedThreadPool(1)

  def start(): Unit = {
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException(s"$description already running.")
    }
    logger.info(s"Starting $description loop with an interval of ${interval.toMillis}ms.")
    executorService.submit(new Runnable {
      override def run(): Unit = {
        while (running.get()) {
          try {
            Thread.sleep(interval.toMillis)
            thunk
          }
          catch {
            case _: InterruptedException =>
            case t: Throwable =>
              logger.warn("Failed to renew the Kerberos ticket", t)
          }
        }
      }
    })
  }

  override def close(): Unit = {
    if (running.compareAndSet(true, false)) {
      executorService.shutdownNow()
      executorService.awaitTermination(10000, TimeUnit.MILLISECONDS)
    }
  }
}
