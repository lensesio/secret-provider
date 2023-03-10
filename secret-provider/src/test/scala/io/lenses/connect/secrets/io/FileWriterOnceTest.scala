package io.lenses.connect.secrets.io

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import scala.io.Source
import scala.util.Success
import scala.util.Try
import scala.util.Using

class FileWriterOnceTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {
  private val folder = new File(UUID.randomUUID().toString)
  folder.deleteOnExit()
  private val writer = new FileWriterOnce(folder.toPath)

  override protected def beforeAll(): Unit =
    folder.mkdir()

  override protected def afterAll(): Unit = {
    folder.listFiles().foreach(f => Try(f.delete()))
    folder.delete()
  }

  test("writes the file") {
    val content = Array(1, 2, 3).map(_.toByte)
    writer.write("thisone", content, "key1")
    val file = Paths.get(folder.toPath.toString, "thisone").toFile
    file.exists() shouldBe true

    Using(Source.fromFile(file))(_.size) shouldBe Success(content.length)
  }

  test("does not write the file twice") {
    val content1 = Array(1, 2, 3).map(_.toByte)
    val fileName = "thissecond"
    writer.write(fileName, content1, "key1")
    val file = Paths.get(folder.toPath.toString, fileName).toFile
    file.exists() shouldBe true

    Using(Source.fromFile(file))(_.size) shouldBe Success(content1.length)

    val content2 = Array(1, 2, 3, 4, 5, 6, 7, 8).map(_.toByte)
    writer.write(fileName, content2, "key1")
    Using(Source.fromFile(file))(_.size) shouldBe Success(content1.length)

  }
}
