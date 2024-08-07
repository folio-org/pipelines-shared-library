package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import hudson.util.Secret

class EurekaUser {

  String uuid = ""

  EurekaUserGroup patronGroup = null

  boolean active = true

  String username = ""

  Secret password = Secret.fromString("")

  String firstName = ""

  String lastName = ""

  String email = "noreply@ci.folio.org"

  String preferredContactTypeId = "002"

  String type = "staff"

  Date expirationDate = null

  /**
   * Returns password in plain text form.
   *
   * @return String Password in plain text.
   */
  String getPasswordPlainText() {
    return password.getPlainText()
  }

  /**
   * Checks if the UUID is set for the user.
   * Throws an exception if it's not set.
   */
  void checkUuid() {
    if (!this.uuid) {
      throw new IllegalStateException("UUID is not set for the user")
    }
  }

  /**
   * Returns string representation of the object.
   * Password and token are not included for security reasons.
   *
   * @return String String representation of the object.
   */
  @NonCPS
  @Override
  String toString() {
    return """
    OkapiUser:
      {
        "username": "$username"
        , "group":  "${patronGroup ? (patronGroup.uuid.trim() == "" ? patronGroup.group : patronGroup.uuid) : ""}"
        , "active": $active
        , "type": $type
        , "personal": {
            , firstName: "$firstName"
            , lastName: "$lastName"
            , email: "$email"
            , "preferredContactTypeId": "$preferredContactTypeId"
        }
        , "expirationDate": "$expirationDate"
        , "id": "$uuid"
      }"""
  }
}
