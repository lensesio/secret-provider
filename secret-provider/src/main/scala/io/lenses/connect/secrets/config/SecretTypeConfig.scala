package io.lenses.connect.secrets.config

import io.lenses.connect.secrets.config.SecretType.SecretType
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.common.config.ConfigDef.Importance
import org.apache.kafka.common.config.ConfigDef.Type
import org.apache.kafka.connect.errors.ConnectException

import scala.util.Try

object SecretType extends Enumeration {

  type SecretType = Value
  val STRING, JSON = Value
}
object SecretTypeConfig {
  val SECRET_TYPE: String = "secret.type"

  def addSecretTypeToConfigDef(configDef: ConfigDef): ConfigDef =
    configDef.define(
      SECRET_TYPE,
      Type.STRING,
      "",
      Importance.LOW,
      "Type of secret to retrieve (string, json)",
    )

  def lookupAndValidateSecretTypeValue(propLookup: String => String): SecretType = {
    for {
      secretTypeString <- Try(propLookup(SecretTypeConfig.SECRET_TYPE)).toOption.filterNot(st =>
        st == null || st.isEmpty,
      )
      secretTypeEnum <- Try {
        SecretType.withName(secretTypeString.toUpperCase)
      }.toEither.left.map(_ =>
        throw new ConnectException(
          s"$secretTypeString is not a valid secret type. Please check your ${SecretTypeConfig.SECRET_TYPE} property.",
        ),
      ).toOption
    } yield secretTypeEnum
  }.getOrElse(SecretType.JSON)

}
