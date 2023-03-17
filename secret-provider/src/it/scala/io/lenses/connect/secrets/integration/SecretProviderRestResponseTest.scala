package io.lenses.connect.secrets.integration

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SecretProviderRestResponseTest extends AnyFunSuite with Matchers {

  private val sampleJson =
    """{"request_id":"41398853-3a2f-ba6b-7c37-23b201941071","lease_id":"","renewable":false,"lease_duration":20,"data": {"data":{"myVaultSecretKey":"myVaultSecretValue"}, "metadata":{"created_time":"2023-02-28T10:49:56.854615Z","custom_metadata":null,"deletion_time":"","destroyed":false,"version":1}},"wrap_info":null,"warnings":null,"auth":null}"""

  test("Should unmarshal sample json") {
    val sprr = SecretProviderRestResponse.fromJson(sampleJson)

    sprr.requestId should be("41398853-3a2f-ba6b-7c37-23b201941071")
    sprr.leaseDuration should be(20)
    sprr.data.data should be(Map[String, Any]("myVaultSecretKey" -> "myVaultSecretValue"))
    sprr.data.metadata.toSet should be(
      Set(
        "createdTime"    -> "2023-02-28T10:49:56.854615Z",
        "customMetadata" -> null,
        "deletionTime"   -> "",
        "destroyed"      -> false,
        "version"        -> Integer.valueOf(1),
      ),
    )
  }

}
