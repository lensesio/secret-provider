/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */
package io.lenses.connect.secrets.io

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths

import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.utils.WithRetry

import scala.concurrent.duration._
import scala.util.Try

trait FileWriter {
  def write(fileName: String, content: Array[Byte], key: String): Path
}

class FileWriterOnce(rootPath: Path)
    extends FileWriter
    with WithRetry
    with StrictLogging {
  rootPath.toFile.mkdirs()
  def write(fileName: String, content: Array[Byte], key: String): Path = {
    val fullPath = Paths.get(rootPath.toString, fileName)
    val file = fullPath.toFile
    if (file.exists()) fullPath
    else {
      val tempFile = Paths.get(rootPath.toString, fileName + ".bak").toFile
      withRetry(10, Some(500.milliseconds)) {
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()
        val fos = new BufferedOutputStream(new FileOutputStream(tempFile))
        try {
          fos.write(content)
          fos.flush()
          logger.info(
            s"Payload written to [${file.getAbsolutePath}] for key [$key]"
          )
        } finally {
          Try(fos.close())
        }
        Try(tempFile.renameTo(file))
      }
      fullPath
    }
  }
}
