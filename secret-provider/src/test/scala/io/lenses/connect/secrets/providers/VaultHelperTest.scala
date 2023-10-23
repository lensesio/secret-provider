package io.lenses.connect.secrets.providers
import io.github.jopenlibs.vault.Vault
import io.github.jopenlibs.vault.response.DatabaseResponse
import io.github.jopenlibs.vault.response.LogicalResponse
import io.lenses.connect.secrets.cache.Ttl
import io.lenses.connect.secrets.cache.ValueWithTtl
import io.lenses.connect.secrets.io.FileWriter
import org.mockito.Answers
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar
import java.time._
import java.util.{ Map => JavaMap }
import scala.jdk.CollectionConverters._

class VaultHelperTest
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterAll
    with MockitoSugar
    with OptionValues
    with EitherValues {

  private val notFoundResponse = {
    val emptyResponse = mock[LogicalResponse](Answers.RETURNS_DEEP_STUBS)
    when(emptyResponse.getRestResponse.getStatus).thenReturn(404)
    when(emptyResponse.getRestResponse.getBody).thenReturn("Not found".getBytes)
    emptyResponse
  }

  private val validResponse: LogicalResponse = {
    val sampleResponseData: JavaMap[String, String] = Map("key1" -> "value1", "key2" -> "value2").asJava
    val sampleResponse:     LogicalResponse         = mock[LogicalResponse](Answers.RETURNS_DEEP_STUBS)
    when(sampleResponse.getData).thenReturn(sampleResponseData)
    when(sampleResponse.getRestResponse.getStatus).thenReturn(200)
    sampleResponse
  }

  private val validDatabaseResponse: DatabaseResponse = {
    val sampleResponseData: JavaMap[String, String] = Map("key1" -> "value1", "key2" -> "value2").asJava
    val sampleResponse:     DatabaseResponse        = mock[DatabaseResponse](Answers.RETURNS_DEEP_STUBS)
    when(sampleResponse.getData).thenReturn(sampleResponseData)
    when(sampleResponse.getRestResponse.getStatus).thenReturn(200)
    sampleResponse
  }

  private val clock:      Clock          = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  private val defaultTtl: Duration       = Duration.ofMinutes(10)
  private val expiry:     OffsetDateTime = clock.instant().plus(defaultTtl).atZone(clock.getZone).toOffsetDateTime

  private val fileWriterCreateFn: () => Option[FileWriter] = () => None

  test("VaultHelper.lookup should fetch secrets from Vault and return them as a ValueWithTtl") {

    val vaultClient: Vault = mock[Vault](Answers.RETURNS_DEEP_STUBS)
    when(vaultClient.logical().read("secret/path")).thenReturn(validResponse)

    val vaultHelper: VaultHelper = new VaultHelper(vaultClient, Some(defaultTtl), fileWriterCreateFn)(clock)

    val result = vaultHelper.lookup("secret/path")

    result.value shouldBe ValueWithTtl[Map[String, String]](Some(Ttl(defaultTtl, expiry)),
                                                            Map("key1" -> "value1", "key2" -> "value2"),
    )
  }

  test(
    "VaultHelper.lookup should fetch secrets from Vault Database Credentials Engine and return them as a ValueWithTtl",
  ) {

    val vaultClient: Vault = mock[Vault](Answers.RETURNS_DEEP_STUBS)
    when(vaultClient.database().creds("sausages")).thenReturn(validDatabaseResponse)

    val vaultHelper: VaultHelper = new VaultHelper(vaultClient, Some(defaultTtl), fileWriterCreateFn)(clock)

    val result = vaultHelper.lookup("database/creds/sausages")

    result.value shouldBe ValueWithTtl[Map[String, String]](Some(Ttl(defaultTtl, expiry)),
                                                            Map("key1" -> "value1", "key2" -> "value2"),
    )
  }

  test("VaultHelper.lookup should handle secrets not found at the specified path") {
    val vaultClient: Vault = mock[Vault](Answers.RETURNS_DEEP_STUBS)
    when(vaultClient.logical().read("empty/path")).thenReturn(notFoundResponse)

    val vaultHelper: VaultHelper = new VaultHelper(vaultClient, Some(defaultTtl), fileWriterCreateFn)(clock)

    val result = vaultHelper.lookup("empty/path")

    result.left.value.getMessage should include("No secrets found at path [empty/path]")
  }

}
