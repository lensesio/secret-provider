/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */
package io.lenses.connect.secrets.utils

import io.lenses.connect.secrets.connect.Encoding
import io.lenses.connect.secrets.connect.Encoding.Encoding

case class EncodingAndId(encoding: Option[Encoding], id: Option[String])
object EncodingAndId {
  val Separator = "_"
  private val encodingPrioritised = List(
    Encoding.UTF8_FILE,
    Encoding.BASE64_FILE,
    Encoding.BASE64,
    Encoding.UTF8,
  )
  def from(key: String): EncodingAndId =
    Option(key).map(_.trim).filter(_.nonEmpty).fold(EncodingAndId(None, None)) {
      value =>
        val encoding = encodingPrioritised
          .map(v => v.toString.toLowerCase() -> v)
          .collectFirst { case (v, e) if value.toLowerCase.startsWith(v) => e }
          .map(identity)

        val id = encoding.flatMap { e =>
          val v = value.drop(e.toString.length).trim
          if (v.isEmpty) None
          else Some(v.drop(1))
        }
        EncodingAndId(encoding, id)
    }
}
