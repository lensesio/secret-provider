package io.lenses.connect.secrets.config

import org.apache.kafka.common.config.types.Password
import org.apache.kafka.connect.errors.ConnectException

import scala.util.Try

case class AWSCredentials(accessKey: String, secretKey: Password)

object AWSCredentials {
  def apply(configs: AWSProviderConfig): Either[Throwable, AWSCredentials] =
    for {
      accessKey <- Try(configs.getString(AWSProviderConfig.AWS_ACCESS_KEY)).toEither
      secretKey <- Try(configs.getPassword(AWSProviderConfig.AWS_SECRET_KEY)).toEither

      accessKeyValidated <- Either.cond(accessKey.nonEmpty,
                                        accessKey,
                                        new ConnectException(
                                          s"${AWSProviderConfig.AWS_ACCESS_KEY} not set",
                                        ),
      )
      secretKeyValidated <- Either.cond(secretKey.value().nonEmpty,
                                        secretKey,
                                        new ConnectException(
                                          s"${AWSProviderConfig.AWS_SECRET_KEY} not set",
                                        ),
      )
    } yield {
      AWSCredentials(accessKeyValidated, secretKeyValidated)
    }
}
