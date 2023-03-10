/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets.config

import org.apache.kafka.common.config.AbstractConfig
import org.apache.kafka.common.config.types.Password
import org.apache.kafka.connect.errors.ConnectException

object AbstractConfigExtensions {
  implicit class AbstractConfigExtension(val config: AbstractConfig) extends AnyVal {
    def getStringOrThrowOnNull(field: String): String =
      Option(config.getString(field)).getOrElse(raiseException(field))

    def getPasswordOrThrowOnNull(field: String): Password = {
      val password = config.getPassword(field)
      if (password == null) raiseException(field)
      password
    }

    private def raiseException(fieldName: String) = throw new ConnectException(
      s"$fieldName not set",
    )
  }
}
