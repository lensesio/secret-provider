/*
 *
 *  * Copyright 2017-2020 Lenses.io Ltd
 *
 */
package io.lenses.connect.secrets.io

import com.typesafe.scalalogging.StrictLogging
import io.lenses.connect.secrets.utils.WithRetry

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.concurrent.duration._
import scala.util.Try

trait FileWriter {
  def write(fileName: String, content: Array[Byte], key: String): Path
}

class FileWriterOnce(rootPath: Path) extends FileWriter with WithRetry with StrictLogging {

  private val folderPermissions = PosixFilePermissions.fromString("rwx------")
  private val filePermissions   = PosixFilePermissions.fromString("rw-------")
  private val folderAttributes =
    PosixFilePermissions.asFileAttribute(folderPermissions)
  private val fileAttributes =
    PosixFilePermissions.asFileAttribute(filePermissions)

  if (!rootPath.toFile.exists)
    Files.createDirectories(rootPath, folderAttributes)

  def write(fileName: String, content: Array[Byte], key: String): Path = {
    val fullPath = Paths.get(rootPath.toString, fileName)
    val file     = fullPath.toFile
    if (file.exists()) fullPath
    else {
      val tempPath = Paths.get(rootPath.toString, fileName + ".bak")
      val tempFile = tempPath.toFile
      withRetry(10, Some(500.milliseconds)) {
        if (tempFile.exists()) tempFile.delete()
        Files.createFile(tempPath, fileAttributes)
        val fos =
          new BufferedOutputStream(new FileOutputStream(tempFile))
        try {
          fos.write(content)
          fos.flush()
          logger.info(
            s"Payload written to [${file.getAbsolutePath}] for key [$key]",
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
