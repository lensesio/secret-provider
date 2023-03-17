/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */

package io.lenses.connect.secrets

import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.common.config.ConfigData
import org.apache.kafka.connect.errors.ConnectException

import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.util.Base64
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

package object connect extends StrictLogging {

  val FILE_ENCODING: String = "file-encoding"
  val FILE_DIR:      String = "file.dir"
  val FILE_DIR_DESC: String =
    """
      | Location to write any files for any secrets that need to
      | be written to disk. For example java keystores.
      | Files will be written under this directory following the 
      | pattern /file.dir/[path|keyvault]/key
      |""".stripMargin

  val WRITE_FILES: String = "file.write"
  val WRITE_FILES_DESC: String =
    """
      | Boolean flag whether to write secrets to disk.  Defaults
      | to false.  Should be set to true when writing Java
      | keystores etc.
      |""".stripMargin

  object AuthMode extends Enumeration {
    type AuthMode = Value
    val DEFAULT, CREDENTIALS = Value
    def withNameOpt(s: String): Option[Value] = values.find(_.toString == s)
  }

  object Encoding extends Enumeration {
    type Encoding = Value
    val BASE64, BASE64_FILE, UTF8, UTF8_FILE = Value
    def withNameOpt(s: String): Option[Value] = values.find(_.toString == s)
  }

  // get the authmethod
  def getAuthenticationMethod(method: String): AuthMode.Value =
    AuthMode.withNameOpt(method.toUpperCase) match {
      case Some(auth) => auth
      case None =>
        throw new ConnectException(
          s"Unsupported authentication method",
        )
    }

  // base64 decode secret
  def decode(key: String, value: String): String =
    Try(Base64.getDecoder.decode(value)) match {
      case Success(decoded) => decoded.map(_.toChar).mkString
      case Failure(exception) =>
        throw new ConnectException(
          s"Failed to decode value for key [$key]",
          exception,
        )
    }

  def decodeToBytes(key: String, value: String): Array[Byte] =
    Try(Base64.getDecoder.decode(value)) match {
      case Success(decoded) => decoded
      case Failure(exception) =>
        throw new ConnectException(
          s"Failed to decode value for key [$key]",
          exception,
        )
    }

  // decode a key bases on the prefix encoding
  def decodeKey(
    encoding:    Option[Encoding.Value],
    key:         String,
    value:       String,
    writeFileFn: Array[Byte] => String,
  ): String =
    encoding.fold(value) {
      case Encoding.BASE64 => decode(key, value)
      case Encoding.BASE64_FILE =>
        val decoded = decodeToBytes(key, value)
        writeFileFn(decoded)
      case Encoding.UTF8      => value
      case Encoding.UTF8_FILE => writeFileFn(value.getBytes())
    }

  // write secrets to file
  private def writer(file: File, payload: Array[Byte], key: String): Unit =
    Try(file.createNewFile()) match {
      case Success(_) =>
        Try(new FileOutputStream(file)) match {
          case Success(fos) =>
            fos.write(payload)
            logger.info(
              s"Payload written to [${file.getAbsolutePath}] for key [$key]",
            )

          case Failure(exception) =>
            throw new ConnectException(
              s"Failed to write payload to file [${file.getAbsolutePath}] for key [$key]",
              exception,
            )
        }

      case Failure(exception) =>
        throw new ConnectException(
          s"Failed to create file [${file.getAbsolutePath}]  for key [$key]",
          exception,
        )
    }

  // write secrets to a file
  def fileWriter(
    fileName:  String,
    payload:   Array[Byte],
    key:       String,
    overwrite: Boolean = false,
  ): Unit = {
    val file = new File(fileName)
    file.getParentFile.mkdirs()

    if (file.exists()) {
      logger.warn(s"File [$fileName] already exists")
      if (overwrite) {
        writer(file, payload, key)
      }
    } else {
      writer(file, payload, key)
    }
  }

  //calculate the min expiry for secrets and return the configData and expiry
  def getSecretsAndExpiry(
    secrets: Map[String, (String, Option[OffsetDateTime])],
  ): (Option[OffsetDateTime], ConfigData) = {
    val expiryList = mutable.ListBuffer.empty[OffsetDateTime]

    val data = secrets
      .map({
        case (key, (value, expiry)) =>
          expiry.foreach(e => expiryList.append(e))
          (key, value)
      })
      .asJava

    if (expiryList.isEmpty) {
      (None, new ConfigData(data))
    } else {
      val minExpiry = expiryList.min
      val ttl = minExpiry.toInstant.toEpochMilli - OffsetDateTime.now.toInstant
        .toEpochMilli
      (Some(minExpiry), new ConfigData(data, ttl))
    }
  }

  def getFileName(
    rootDir:   String,
    path:      String,
    key:       String,
    separator: String,
  ): String =
    s"${rootDir.stripSuffix(separator)}$separator$path$separator${key.toLowerCase}"
}
