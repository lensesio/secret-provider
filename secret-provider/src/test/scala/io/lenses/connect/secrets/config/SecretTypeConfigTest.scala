package io.lenses.connect.secrets.config

import org.apache.kafka.connect.errors.ConnectException
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

class SecretTypeConfigTest extends AnyFunSuite with Matchers with OptionValues with MockitoSugar {

  test("lookupAndValidateSecretTypeValue should throw accept valid types") {
    SecretTypeConfig.lookupAndValidateSecretTypeValue(mockSecretReturn("string")) should be(SecretType.STRING)
    SecretTypeConfig.lookupAndValidateSecretTypeValue(mockSecretReturn("STRING")) should be(SecretType.STRING)
    SecretTypeConfig.lookupAndValidateSecretTypeValue(mockSecretReturn("json")) should be(SecretType.JSON)
    SecretTypeConfig.lookupAndValidateSecretTypeValue(mockSecretReturn("JSON")) should be(SecretType.JSON)
  }

  test("lookupAndValidateSecretTypeValue should throw error on invalid type") {
    val secRetFn = mockSecretReturn("nonExistentSecretType")
    intercept[ConnectException] {
      SecretTypeConfig.lookupAndValidateSecretTypeValue(secRetFn)
    }.getMessage should startWith("nonExistentSecretType is not a valid secret type")
  }

  test("lookupAndValidateSecretTypeValue should return json by default") {
    SecretTypeConfig.lookupAndValidateSecretTypeValue(mockSecretReturn(null)) should be(SecretType.JSON)
    SecretTypeConfig.lookupAndValidateSecretTypeValue(mockSecretReturn("")) should be(SecretType.JSON)
  }

  private def mockSecretReturn(out: String): String => String = {
    val mockFn = mock[String => String]
    when(mockFn.apply(SecretTypeConfig.SECRET_TYPE)).thenReturn(out)
    mockFn
  }

}
