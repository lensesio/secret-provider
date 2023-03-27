package io.lenses.connect.secrets

import cats.effect.IO
import org.testcontainers.containers.KafkaContainer
import software.amazon.awssdk.core.internal.waiters.{ ResponseOrException => AWSResponseOrException }
package object testcontainers {

  implicit class KafkaContainerOps(kafkaContainer: KafkaContainer) {
    val kafkaPort = 9092

    def bootstrapServers: String =
      s"PLAINTEXT://${kafkaContainer.getNetworkAliases.get(0)}:$kafkaPort"
  }

  implicit class ResponseOrException[R](responseOrException: AWSResponseOrException[R]) {

    def toIO(): IO[R] = IO.fromEither(toEither())

    def toEither(): Either[Throwable, R] =
      Either.cond(
        responseOrException.response().isPresent,
        responseOrException.response().get(),
        responseOrException.exception().get(),
      )
  }

}
