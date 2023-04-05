package io.lenses.connect.secrets.config

import io.lenses.connect.secrets.connect.FILE_DIR
import io.lenses.connect.secrets.connect.WRITE_FILES
import io.lenses.connect.secrets.io.FileWriterOnce
import org.apache.kafka.common.config.AbstractConfig

import java.nio.file.Paths

object FileWriterOptions {
  def apply(config: AbstractConfig): Option[FileWriterOptions] =
    Option.when(config.getBoolean(WRITE_FILES)) {
      FileWriterOptions(config.getString(FILE_DIR))
    }
}

case class FileWriterOptions(
  fileDir: String,
) {
  def createFileWriter(): FileWriterOnce =
    new FileWriterOnce(Paths.get(fileDir, "secrets"))

}
