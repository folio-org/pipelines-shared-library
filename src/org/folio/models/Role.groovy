package org.folio.models

import com.cloudbees.groovy.cps.NonCPS

class Role {

  String uuid = ""

  /**
   * Role name.
   */
  String name = ""

  /**
   * Role description.
   */
  String desc = ""

  /**
   * Returns string representation of the object.
   * Password and token are not included for security reasons.
   *
   * @return String String representation of the object.
   */
  @Override
  @NonCPS
  String toString() {
    return """
    Role:
      {
        "name": "$name"
        , "desc":  "$desc"
        , "id": "$uuid"
      }"""
  }
}
