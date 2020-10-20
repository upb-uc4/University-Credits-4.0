package de.upb.cs.uc4.certificate.model

import de.upb.cs.uc4.shared.client.configuration.RegexCollection
import de.upb.cs.uc4.shared.client.exceptions.SimpleError
import play.api.libs.json.{ Format, Json }

import scala.concurrent.{ ExecutionContext, Future }

case class EncryptedPrivateKey(key: String, iv: String, salt: String) {

  def validate(implicit ec: ExecutionContext): Future[Seq[SimpleError]] = Future {
    val keyRegex = RegexCollection.EncryptedPrivateKey.keyRegex
    val ivRegex = RegexCollection.EncryptedPrivateKey.ivRegex
    val saltRegex = RegexCollection.EncryptedPrivateKey.saltRegex

    if (key.isEmpty && iv.isEmpty && salt.isEmpty) {
      Seq()
    }
    else if (Seq(key, iv, salt).contains("")) {
      Seq(SimpleError("encryptedPrivateKey", "Either all fields must be empty or no fields must be empty."))
    }
    else {
      var errors = List[SimpleError]()

      if (!keyRegex.matches(key)) {
        errors :+= SimpleError("key", "For a non-empty key object, the key must not be longer than 8192 characters.")
      }
      if (!ivRegex.matches(iv)) {
        errors :+= SimpleError("iv", "For a non-empty key object, the iv must not be longer than 64 characters.")
      }
      if (!saltRegex.matches(salt)) {
        errors :+= SimpleError("salt", "For a non-empty key object, the salt must not be longer than 256 characters.")
      }

      errors
    }
  }
}

object EncryptedPrivateKey {
  implicit val format: Format[EncryptedPrivateKey] = Json.format
}
