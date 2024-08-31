package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import hudson.util.Secret
import org.folio.rest_v2.eureka.kong.UserGroups

class User {

  String uuid = ""

  UserGroup patronGroup = null

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

  Map toMap(){
    Map ret = [
      username: username,
      active: active,
      type: type,
      personal: [
        firstName: firstName,
        lastName: lastName,
        email: email,
        preferredContactTypeId: preferredContactTypeId
      ],
      expirationDate: expirationDate
    ]

    if(patronGroup)
      ret.put("group", patronGroup.uuid.trim() == "" ? patronGroup.group : patronGroup.uuid)

    if(uuid.trim())
      ret.put("id", uuid)

    return ret
  }

  static User getUserFromContent(Map content, EurekaTenant tenant, UserGroups groupsAPI){
    return new User(
      uuid: content.id,
      username: content.username,
      patronGroup: groupsAPI.getUserGroup(tenant, content.patronGroup as String),
      active: content.active,
      type: content.type,
      firstName: content.personal.firstName,
      lastName: content.personal.lastName,
      email: content.personal.email,
      preferredContactTypeId: content.personal.preferredContactTypeId
    )
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
    User:
      {
        "username": "$username"
        , "group":  "${patronGroup ? (patronGroup.uuid.trim() == "" ? patronGroup.group : patronGroup.uuid) : ""}"
        , "active": $active
        , "type": "$type"
        , "personal": {
            "firstName": "$firstName"
            , "lastName": "$lastName"
            , "email": "$email"
            , "preferredContactTypeId": "$preferredContactTypeId"
        }
        , "expirationDate": "$expirationDate"
        , "id": "$uuid"
      }"""
  }
}
