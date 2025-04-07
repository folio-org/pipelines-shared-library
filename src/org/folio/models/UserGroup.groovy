package org.folio.models

class UserGroup {

  String uuid = ""

  /**
   * Group name user belongs to.
   */
  String group = ""

  /**
   * Group description.
   */
  String desc = ""

  Map toMap(){
    Map ret = [
      group: group,
      desc: desc
    ]

    if(uuid.trim())
      ret.put("id", uuid)

    return ret
  }

  static UserGroup getGroupFromContent(Map content){
    return new UserGroup(
      uuid: content.id,
      group: content.group,
      desc: content.desc
    )
  }

  /**
   * Returns string representation of the object.
   * Password and token are not included for security reasons.
   *
   * @return String String representation of the object.
   */
  @Override
  String toString() {
    return """
    UserGroup:
      {
        "group": "$group"
        , "desc":  "$desc"
        , "id": "$uuid"
      }"""
  }
}
