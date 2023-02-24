package io.lenses.connect.secrets

import org.testcontainers.containers.KafkaContainer

package object testcontainers {

  implicit class KafkaContainerOps(kafkaContainer: KafkaContainer) {
    val kafkaPort = 9092

    def bootstrapServers: String =
      s"PLAINTEXT://${kafkaContainer.getNetworkAliases.get(0)}:$kafkaPort"
  }
}
