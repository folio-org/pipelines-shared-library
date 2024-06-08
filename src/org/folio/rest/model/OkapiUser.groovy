package org.folio.rest.model

class OkapiUser implements Serializable {
  String username
  String password
  String firstName = ''
  String lastName = ''
  String email = ''
  String groupName
  String barcode
  ArrayList permissions
  String permissionsId
  String uuid
  String token
}
