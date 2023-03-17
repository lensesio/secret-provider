package io.lenses.connect.secrets.testcontainers

import com.github.dockerjava.api.model.Ulimit
import io.lenses.connect.secrets.testcontainers.KafkaConnectContainer.defaultNetworkAlias
import io.lenses.connect.secrets.testcontainers.KafkaConnectContainer.defaultRestPort
import io.lenses.connect.secrets.testcontainers.connect.ConfigProviders
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

import java.time.Duration
import scala.jdk.CollectionConverters.MapHasAsJava

class KafkaConnectContainer(
  dockerImage:          String,
  networkAlias:         String              = defaultNetworkAlias,
  restPort:             Int                 = defaultRestPort,
  connectPluginPath:    Map[String, String] = Map.empty,
  kafkaContainer:       KafkaContainer,
  maybeConfigProviders: Option[ConfigProviders],
) extends GenericContainer[KafkaConnectContainer](DockerImageName.parse(dockerImage)) {
  require(kafkaContainer != null, "You must define the kafka container")

  withNetwork(kafkaContainer.getNetwork)
  withNetworkAliases(networkAlias)
  withExposedPorts(restPort)
  waitingFor(
    Wait
      .forHttp("/connectors")
      .forPort(restPort)
      .forStatusCode(200),
  ).withStartupTimeout(Duration.ofSeconds(120))
  withCreateContainerCmdModifier { cmd =>
    val _ =
      cmd.getHostConfig.withUlimits(Array(new Ulimit("nofile", 65536L, 65536L)))
  }

  connectPluginPath.foreach {
    case (k, v) => withFileSystemBind(v, s"/usr/share/plugins/$k")
  }

  withEnv("CONNECT_KEY_CONVERTER_SCHEMAS_ENABLE", "false")
  withEnv("CONNECT_VALUE_CONVERTER_SCHEMAS_ENABLE", "false")
  withEnv(
    "CONNECT_KEY_CONVERTER",
    "org.apache.kafka.connect.storage.StringConverter",
  )
  withEnv(
    "CONNECT_VALUE_CONVERTER",
    "org.apache.kafka.connect.storage.StringConverter",
  )

  withEnv("CONNECT_REST_ADVERTISED_HOST_NAME", networkAlias)
  withEnv("CONNECT_REST_PORT", restPort.toString)
  withEnv("CONNECT_BOOTSTRAP_SERVERS", kafkaContainer.bootstrapServers)
  withEnv("CONNECT_GROUP_ID", "io/lenses/connect/secrets/integration/connect")
  withEnv("CONNECT_CONFIG_STORAGE_TOPIC", "connect_config")
  withEnv("CONNECT_OFFSET_STORAGE_TOPIC", "connect_offset")
  withEnv("CONNECT_STATUS_STORAGE_TOPIC", "connect_status")
  withEnv("CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR", "1")
  withEnv("CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR", "1")
  withEnv("CONNECT_STATUS_STORAGE_REPLICATION_FACTOR", "1")
  withEnv(
    "CONNECT_PLUGIN_PATH",
    "/usr/share/java,/usr/share/confluent-hub-components,/usr/share/plugins",
  )

  maybeConfigProviders.map(cpc => withEnv(cpc.toEnvMap.asJava))

  lazy val hostNetwork = new HostNetwork()

  class HostNetwork {
    def restEndpointUrl: String = s"http://$getHost:${getMappedPort(restPort)}"
  }

}
object KafkaConnectContainer {
  private val dockerImage =
    DockerImageName.parse("confluentinc/cp-kafka-connect")
  private val defaultConfluentPlatformVersion: String =
    sys.env.getOrElse("CONFLUENT_VERSION", "7.3.1")
  private val defaultNetworkAlias = "connect"
  private val defaultRestPort     = 8083

  def apply(
    confluentPlatformVersion: String              = defaultConfluentPlatformVersion,
    networkAlias:             String              = defaultNetworkAlias,
    restPort:                 Int                 = defaultRestPort,
    connectPluginPathMap:     Map[String, String] = Map.empty,
    kafkaContainer:           KafkaContainer,
    maybeConfigProviders:     Option[ConfigProviders],
  ): KafkaConnectContainer =
    new KafkaConnectContainer(
      dockerImage.withTag(confluentPlatformVersion).toString,
      networkAlias,
      restPort,
      connectPluginPathMap,
      kafkaContainer,
      maybeConfigProviders,
    )
}
