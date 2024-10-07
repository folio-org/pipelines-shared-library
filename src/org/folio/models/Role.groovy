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

  Map toMap(){
    Map ret = [
      name: name,
      description: desc
    ]

    if(uuid.trim())
      ret.put("id", uuid)

    return ret
  }

  static Role getRoleFromContent(Map content){
    return new Role(
      uuid: content.id,
      name: content.name,
      desc: content.description
    )
  }

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
