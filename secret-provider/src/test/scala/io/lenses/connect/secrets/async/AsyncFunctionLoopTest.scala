/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.async

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt

class AsyncFunctionLoopTest extends AnyFunSuite with Matchers {
  test("it loops 5 times in 10 seconds with 2s delay") {
    val countDownLatch = new CountDownLatch(5)
    val looper = new AsyncFunctionLoop(2.seconds, "test")(
      countDownLatch.countDown(),
    )
    looper.start()
    countDownLatch.await(11000, TimeUnit.MILLISECONDS) shouldBe true
    looper.close()
  }
}
