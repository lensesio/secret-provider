package io.lenses.connect.secrets.testcontainers

import cats.effect.IO
import cats.effect.Resource
import io.github.jopenlibs.vault.Vault
import io.github.jopenlibs.vault.VaultConfig
import io.github.jopenlibs.vault.api.mounts.MountPayload
import io.github.jopenlibs.vault.api.mounts.MountType
import io.github.jopenlibs.vault.api.mounts.TimeToLive
import io.github.jopenlibs.vault.response.MountResponse
import com.typesafe.scalalogging.LazyLogging
import io.lenses.connect.secrets.testcontainers.VaultContainer._
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.vault.{ VaultContainer => JavaVaultContainer }

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.MapHasAsJava

case class LeaseInfo(defaultLeaseTimeSeconds: Int, maxLeaseTimeSeconds: Int) {
  def toDurString(): String =
    s""""default_lease_ttl": "${defaultLeaseTimeSeconds}s", "max_lease_ttl": "${maxLeaseTimeSeconds}s","""
}

class VaultContainer(maybeLeaseInfo: Option[LeaseInfo] = Option.empty)
    extends SingleContainer[JavaVaultContainer[_]]
    with Matchers
    with LazyLogging {

  private val token: String = UUID.randomUUID().toString

  override val container: JavaVaultContainer[_] = new JavaVaultContainer(
    VaultDockerImageName,
  )
    .withNetworkAliases(defaultNetworkAlias)

  container.withEnv(
    "VAULT_LOCAL_CONFIG", //"listener": [{"tcp": { "address": "0.0.0.0:8200", "tls_disable": true}}],
    s"""
       |{${maybeLeaseInfo.fold("")(_.toDurString())} "ui": true}""".stripMargin,
  )

  container.withVaultToken(token)

  def vaultClientResource: Resource[IO, Vault] =
    Resource.make(
      IO(
        new Vault(
          new VaultConfig()
            .address(container.getHttpHostAddress)
            .token(token)
            .engineVersion(2)
            .build(),
        ),
      ),
    )(_ => IO.unit)

  def configureMount(secretTtlSeconds: Long): IO[MountResponse] =
    vaultClientResource.use(client =>
      IO {
        logger.info("Configuring mount")
        val mountPayload = new MountPayload()
        mountPayload.defaultLeaseTtl(TimeToLive.of(secretTtlSeconds.intValue(), TimeUnit.SECONDS))
        client.mounts().enable(vaultRootPath, MountType.KEY_VALUE, mountPayload)
      },
    )

  def rotateSecret(
    secretTtlSeconds: Long,
  ): IO[String] =
    vaultClientResource.use(client =>
      IO {
        val time           = System.currentTimeMillis()
        val newSecretValue = s"${vaultSecretPrefix}_$time"
        logger.info(s"Rotating secret to {}", newSecretValue)
        client
          .logical()
          .write(
            vaultSecretPath,
            Map[String, Object](
              vaultSecretKey -> s"${vaultSecretPrefix}_$time",
              "ttl"          -> s"${secretTtlSeconds}s",
              "created"      -> Long.box(time),
              "expires"      -> Long.box(time + (secretTtlSeconds * 1000)),
            ).asJava,
          )
        newSecretValue
      },
    )

  def vaultToken = token

  def vaultAddress = container.getHttpHostAddress

  def networkVaultAddress: String =
    String.format("http://%s:%s", defaultNetworkAlias, vaultPort)

}

object VaultContainer {
  val VaultDockerImageName: DockerImageName =
    DockerImageName.parse("vault").withTag("1.12.3")
  val vaultRootPath     = "rotate-test" // was: secret/
  val vaultSecretPath   = s"$vaultRootPath/myVaultSecretPath"
  val vaultSecretKey    = "myVaultSecretKey"
  val vaultSecretPrefix = "myVaultSecretValue"

  private val defaultNetworkAlias = "vault"
  private val vaultPort: Int = 8200
}
