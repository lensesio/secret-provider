package io.lenses.connect.secrets.test

import com.typesafe.scalalogging.LazyLogging
import io.lenses.connect.secrets.test.VaultStateValidator.validateSecret
import org.apache.kafka.connect.sink.SinkRecord
import org.apache.kafka.connect.sink.SinkTask
import org.apache.kafka.connect.sink.SinkTaskContext

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.MapHasAsScala

class TestSinkTask extends SinkTask with LazyLogging {

  private implicit var vaultState: VaultState = _

  override def start(props: util.Map[String, String]): Unit = {
    val scalaProps = props.asScala.toMap
    logger.info("start(props:{})", scalaProps)
    vaultState = VaultState(scalaProps)
    logger.info("start(vaultState:{})", vaultState)
  }

  override def put(records: util.Collection[SinkRecord]): Unit = {
    logger.info("put(records:{})", records.asScala)
    validateSecret()
  }

  override def stop(): Unit =
    logger.info("stop()")

  override def initialize(context: SinkTaskContext): Unit = {
    super.initialize(context)
    logger.info("initialize(configs:{})", context.configs().asScala)
  }

  override def version(): String = "0.0.1a"
}
