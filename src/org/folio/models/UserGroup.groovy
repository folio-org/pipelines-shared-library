package org.folio.models

class EurekaUserGroup {

  String uuid = ""

  /**
   * Group name user belongs to.
   */
  String group = ""

  /**
   * Group description.
   */
  String desc = ""

  /**
   * Returns string representation of the object.
   * Password and token are not included for security reasons.
   *
   * @return String String representation of the object.
   */
  @Override
  String toString() {
    return """
    EurekaGroup:
      {
        "group": "$group"
        , "desc":  "$desc"
        , "id": "$uuid"
      }"""
  }
}
