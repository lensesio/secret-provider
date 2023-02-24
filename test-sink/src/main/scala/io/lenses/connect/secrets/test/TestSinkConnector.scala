package io.lenses.connect.secrets.test

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.common.config.ConfigDef.Importance
import org.apache.kafka.connect.connector.Task
import org.apache.kafka.connect.sink.SinkConnector

import java.util
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava

class TestSinkConnector extends SinkConnector with LazyLogging {

  private val storedProps: util.Map[String, String] =
    new util.HashMap[String, String]()

  override def start(props: util.Map[String, String]): Unit = {
    logger.info("start(props:{})", props.asScala)
    storedProps.putAll(props)
  }

  override def taskClass(): Class[_ <: Task] = classOf[TestSinkTask]

  override def taskConfigs(
    maxTasks: Int,
  ): util.List[util.Map[String, String]] = {
    logger.info("testConfigs(maxTasks:{})", maxTasks)
    List.fill(1)(storedProps).asJava
  }

  override def stop(): Unit = {
    logger.info("stop()")
    ()
  }

  override def config(): ConfigDef = new ConfigDef()
    .define("test.sink.vault.host", ConfigDef.Type.STRING, Importance.LOW, "host")
    .define("test.sink.vault.token", ConfigDef.Type.STRING, Importance.LOW, "token")
    .define("test.sink.secret.value", ConfigDef.Type.STRING, Importance.LOW, "value")
    .define("test.sink.secret.path", ConfigDef.Type.STRING, Importance.LOW, "path")
    .define("test.sink.secret.key", ConfigDef.Type.PASSWORD, Importance.LOW, "key")

  override def version(): String = "0.0.1a"
}
