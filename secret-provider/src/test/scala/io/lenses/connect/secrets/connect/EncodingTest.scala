package io.lenses.connect.secrets.connect

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class EncodingTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  private val validEncodingsWithoutHyphens = Table(
    ("input", "expectedResult"),
    ("BASE64", Encoding.BASE64),
    ("base64", Encoding.BASE64),
    ("BASE-64", Encoding.BASE64),
    ("BASE-64_FILE", Encoding.BASE64_FILE),
    ("base64_file", Encoding.BASE64_FILE),
    ("UTF8", Encoding.UTF8),
    ("utf8", Encoding.UTF8),
    ("UTF-8", Encoding.UTF8),
    ("UTF-8_FILE", Encoding.UTF8_FILE),
    ("utf8_file", Encoding.UTF8_FILE),
  )

  test("withoutHyphensInsensitiveOpt should recognize valid encodings") {
    forAll(validEncodingsWithoutHyphens) { (input, expectedResult) =>
      Encoding.withoutHyphensInsensitiveOpt(input) should be(Some(expectedResult))
    }
  }

  test("withoutHyphensInsensitiveOpt should return None for an invalid input 'UNKNOWN-ENCODING'") {
    Encoding.withoutHyphensInsensitiveOpt("UNKNOWN-ENCODING") should be(None)
  }
}
