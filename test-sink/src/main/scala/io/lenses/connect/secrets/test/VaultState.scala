package io.lenses.connect.secrets.test

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig

case class VaultState(
  vault:       Vault,
  secretValue: String,
  secretPath:  String,
  secretKey:   String,
)

object VaultState {
  def apply(props: Map[String, String]): VaultState = {

    val vaultHost = props.getOrElse(
      "test.sink.vault.host",
      throw new IllegalStateException("No test.sink.vault.host"),
    )
    val vaultToken = props.getOrElse(
      "test.sink.vault.token",
      throw new IllegalStateException("No test.sink.vault.token"),
    )
    val secret = props.getOrElse(
      "test.sink.secret.value",
      throw new IllegalStateException("No test.sink.secret.value"),
    )
    val secretPath = props.getOrElse(
      "test.sink.secret.path",
      throw new IllegalStateException("No test.sink.secret.path"),
    )
    val secretKey = props.getOrElse(
      "test.sink.secret.key",
      throw new IllegalStateException("No test.sink.secret.key"),
    )
    VaultState(
      new Vault(
        new VaultConfig()
          .address(vaultHost)
          .token(vaultToken)
          .build(),
      ),
      secret,
      secretPath,
      secretKey,
    )
  }
}
