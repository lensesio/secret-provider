package io.lenses.connect.secrets.testcontainers.scalatest

import cats.effect.IO
import cats.effect.Resource
import com.typesafe.scalalogging.LazyLogging
import io.lenses.connect.secrets.testcontainers.connect.ConfigProviders
import io.lenses.connect.secrets.testcontainers.connect.KafkaConnectClient
import io.lenses.connect.secrets.testcontainers.KafkaConnectContainer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.TestSuite
import org.scalatest.concurrent.Eventually
import org.scalatest.time.Minute
import org.scalatest.time.Span
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util
import java.util.Properties
import java.util.UUID
import java.util.stream.Collectors
import scala.collection.mutable.ListBuffer
import scala.util.Try

trait SecretProviderContainerPerSuite extends BeforeAndAfterAll with Eventually with LazyLogging { this: TestSuite =>

  def getConfigProviders(): Option[ConfigProviders] = Option.empty

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(1, Minute))

  private val confluentPlatformVersion: String = {
    val (vers, from) = sys.env.get("CONFLUENT_VERSION") match {
      case Some(value) => (value, "env")
      case None        => ("7.3.1", "default")
    }
    logger.info("Selected confluent version {} from {}", vers, from)
    vers
  }

  val network: Network = Network.SHARED

  def modules: Seq[String]

  lazy val kafkaContainer: KafkaContainer =
    new KafkaContainer(
      DockerImageName.parse(s"confluentinc/cp-kafka:$confluentPlatformVersion"),
    ).withNetwork(network)
      .withNetworkAliases("kafka")
      .withLogConsumer(new Slf4jLogConsumer(logger.underlying))

  lazy val kafkaConnectContainer: KafkaConnectContainer = {
    KafkaConnectContainer(
      kafkaContainer       = kafkaContainer,
      connectPluginPathMap = connectPluginPath(),
      maybeConfigProviders = getConfigProviders(),
    ).withNetwork(network)
      .withLogConsumer(new Slf4jLogConsumer(logger.underlying))
  }

  implicit lazy val kafkaConnectClient: KafkaConnectClient =
    new KafkaConnectClient(kafkaConnectContainer)

  override def beforeAll(): Unit = {
    kafkaContainer.start()
    kafkaConnectContainer.start()
    super.beforeAll()
  }

  override def afterAll(): Unit =
    try super.afterAll()
    finally {
      kafkaConnectContainer.stop()
      kafkaContainer.stop()
    }

  def connectPluginPath(): Map[String, String] = {

    val dir: String = detectUserOrGithubDir

    modules.map { module: String =>
      val regex = s".*$module.*.jar"
      val files: util.List[Path] = Files
        .find(
          Paths.get(
            String.join(
              File.separator,
              dir,
              module,
              "target",
            ),
          ),
          3,
          (p, _) => p.toFile.getName.matches(regex),
        )
        .collect(Collectors.toList())
      if (files.isEmpty)
        throw new RuntimeException(
          s"""Please run `sbt "project $module" assembly""",
        )
      module -> files.get(0).getParent.toString
    }.toMap

  }

  private def detectUserOrGithubDir = {
    val userDir  = sys.props("user.dir")
    val githubWs = Try(Option(sys.env("GITHUB_WORKSPACE"))).toOption.flatten

    logger.info("userdir: {} githubWs: {}", userDir, githubWs)

    githubWs.getOrElse(userDir)
  }

  def makeStringStringProducerResource(): Resource[IO, KafkaProducer[String, String]] =
    makeProducerResource(classOf[StringSerializer], classOf[StringSerializer])

  def makeProducerResource[K, V](
    keySer:   Class[_],
    valueSer: Class[_],
  ): Resource[IO, KafkaProducer[K, V]] = {
    val props = new Properties
    props.put(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafkaContainer.getBootstrapServers,
    )
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, 0)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySer)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSer)
    Resource.make(IO(new KafkaProducer[K, V](props)))(r => IO(r.close()))
  }

  def createConsumer(): KafkaConsumer[String, String] = {
    val props = new Properties
    props.put(
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafkaContainer.getBootstrapServers,
    )
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "cg-" + UUID.randomUUID())
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    new KafkaConsumer[String, String](
      props,
      new StringDeserializer(),
      new StringDeserializer(),
    )
  }

  /**
    * Drain a kafka topic.
    *
    * @param consumer the kafka consumer
    * @param expectedRecordCount the expected record count
    * @tparam K the key type
    * @tparam V the value type
    * @return the records
    */
  def drain[K, V](
    consumer:            KafkaConsumer[K, V],
    expectedRecordCount: Int,
  ): List[ConsumerRecord[K, V]] = {
    val allRecords = ListBuffer[ConsumerRecord[K, V]]()

    eventually {
      consumer
        .poll(Duration.ofMillis(50))
        .iterator()
        .forEachRemaining(e => allRecords :+ e)
      assert(allRecords.size == expectedRecordCount)
    }
    allRecords.toList
  }
}
