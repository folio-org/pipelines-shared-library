package org.folio.rest.model

class OkapiTenant implements Serializable {
  String id
  String name
  String description
  Map tenantParameters
  Map queryParameters
  OkapiUser adminUser = new OkapiUser()
  String okapiVersion
  Map index
  String kb_api_key
}
