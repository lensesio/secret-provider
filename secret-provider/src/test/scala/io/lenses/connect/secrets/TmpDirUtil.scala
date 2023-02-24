package io.lenses.connect.secrets

import java.nio.file.FileSystems

object TmpDirUtil {

  val separator: String = FileSystems.getDefault.getSeparator

  def getTempDir: String =
    System.getProperty("java.io.tmpdir").stripSuffix(separator)
}
