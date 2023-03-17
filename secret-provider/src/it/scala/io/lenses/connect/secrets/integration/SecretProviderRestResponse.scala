package io.lenses.connect.secrets.integration

import com.bettercloud.vault.rest.RestResponse
import org.json4s.DefaultFormats
import org.json4s._
import org.json4s.native.JsonMethods._

case class DataResponse(
  data:     Map[String, Any],
  metadata: Map[String, Any],
)
case class SecretProviderRestResponse(
  requestId:     String,
  leaseId:       String,
  renewable:     Boolean,
  leaseDuration: BigInt,
  data:          DataResponse,
  wrapInfo:      Any, // todo what is this?
  warnings:      Any, // todo
  auth:          Any, // todo
)

case object SecretProviderRestResponse {

  def fromRestResponse(rr: RestResponse) =
    fromJson(new String(rr.getBody))

  def fromJson(json: String) = {
    implicit val formats: DefaultFormats.type = DefaultFormats

    parse(json).camelizeKeys.extract[SecretProviderRestResponse]
  }
}
