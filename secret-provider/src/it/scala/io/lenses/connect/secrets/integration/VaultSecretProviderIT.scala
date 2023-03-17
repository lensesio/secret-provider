package io.lenses.connect.secrets.integration

import cats.effect.FiberIO
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxOptionId
import io.lenses.connect.secrets.testcontainers.LeaseInfo
import io.lenses.connect.secrets.testcontainers.VaultContainer
import io.lenses.connect.secrets.testcontainers.VaultContainer.vaultSecretKey
import io.lenses.connect.secrets.testcontainers.VaultContainer.vaultSecretPath
import io.lenses.connect.secrets.testcontainers.connect.ConfigProvider
import io.lenses.connect.secrets.testcontainers.connect.ConfigProviders
import io.lenses.connect.secrets.testcontainers.connect.ConnectorConfiguration
import io.lenses.connect.secrets.testcontainers.connect.KafkaConnectClient
import io.lenses.connect.secrets.testcontainers.connect.StringCnfVal
import io.lenses.connect.secrets.testcontainers.scalatest.SecretProviderContainerPerSuite
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class VaultSecretProviderIT
    extends AnyFunSuite
    with SecretProviderContainerPerSuite
    with Matchers
    with BeforeAndAfterAll {

  lazy val secretTtlSeconds = 5

  lazy val container: VaultContainer =
    new VaultContainer(
      LeaseInfo(secretTtlSeconds, secretTtlSeconds).some,
    ).withNetwork(network)

  override def modules: Seq[String] = Seq("secret-provider", "test-sink")

  val topicName     = "vaultVandal"
  val connectorName = "testSink"
  override def beforeAll(): Unit = {
    container.start()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    container.stop()
  }

  test("does not fail with secret TTLs") {
    (for {
      _               <- container.configureMount(secretTtlSeconds)
      initialSecret   <- container.rotateSecret(secretTtlSeconds)
      latestSecretRef <- Ref.of[IO, String](initialSecret)
      _               <- secretUpdater(latestSecretRef)
      _               <- startConnector()
    } yield ()).unsafeRunSync()
  }

  private def startConnector(): IO[Unit] =
    makeStringStringProducerResource().use { prod =>
      withConnectorResource(sinkConfig()).use(_ => writeRecords(prod))
    }

  private def writeRecords(prod: KafkaProducer[String, String]): IO[Unit] =
    IO {
      (1 to 100).foreach { i =>
        writeRecord(prod, i)
        Thread.sleep(250)
      }
    }

  private def writeRecord(
    producer: KafkaProducer[String, String],
    i:        Int,
  ): Unit = {
    val record: ProducerRecord[String, String] =
      new ProducerRecord[String, String](
        topicName,
        s"${i}_${UUID.randomUUID().toString}",
      )
    producer.send(record).get
    producer.flush()
  }

  private def withConnectorResource(
    connectorConfig: ConnectorConfiguration,
    timeoutSeconds:  Long = 10L,
  )(
    implicit
    kafkaConnectClient: KafkaConnectClient,
  ): Resource[IO, String] =
    Resource.make(
      IO {
        val connectorName = connectorConfig.name
        kafkaConnectClient.registerConnector(connectorConfig)
        kafkaConnectClient.configureLogging("org.apache.kafka.connect.runtime.WorkerConfigTransformer", "DEBUG")
        kafkaConnectClient.waitConnectorInRunningState(
          connectorName,
          timeoutSeconds,
        )
        connectorName
      },
    )(connectorName => IO(kafkaConnectClient.deleteConnector(connectorName)))

  private def secretUpdater(ref: Ref[IO, String]): IO[FiberIO[Unit]] =
    (for {
      _           <- IO.sleep(FiniteDuration(secretTtlSeconds, TimeUnit.SECONDS))
      rotated     <- container.rotateSecret(secretTtlSeconds)
      _           <- ref.set(rotated)
      updateAgain <- secretUpdater(ref)
      _           <- updateAgain.join
    } yield ()).start

  private def sinkConfig(): ConnectorConfiguration =
    ConnectorConfiguration(
      connectorName,
      Map(
        "name" -> StringCnfVal(connectorName),
        "connector.class" -> StringCnfVal(
          "io.lenses.connect.secrets.test.TestSinkConnector",
        ),
        "topics" -> StringCnfVal(topicName),
        "key.converter" -> StringCnfVal(
          "org.apache.kafka.connect.storage.StringConverter",
        ),
        "value.converter" -> StringCnfVal(
          "org.apache.kafka.connect.storage.StringConverter",
        ),
        "test.sink.vault.host"  -> StringCnfVal(container.networkVaultAddress),
        "test.sink.vault.token" -> StringCnfVal(container.vaultToken),
        "test.sink.secret.path" -> StringCnfVal(vaultSecretPath),
        "test.sink.secret.key"  -> StringCnfVal(vaultSecretKey),
        "test.sink.secret.value" -> StringCnfVal(
          s"$${vault:$vaultSecretPath:$vaultSecretKey}",
        ),
      ),
    )

  override def getConfigProviders(): Option[ConfigProviders] =
    ConfigProviders(
      Seq(
        ConfigProvider(
          "vault",
          "io.lenses.connect.secrets.providers.VaultSecretProvider",
          Map(
            "vault.addr"           -> container.networkVaultAddress,
            "vault.engine.version" -> "2",
            "vault.auth.method"    -> "token",
            "vault.token"          -> container.vaultToken,
            "default.ttl"          -> "30000",
            "file.write"           -> "false",
          ),
        ),
      ),
    ).some
}
