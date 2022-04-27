package org.folio.rest

class OkapiTenant implements Serializable {
    String id
    String name
    String description
    LinkedHashMap parameters
    OkapiUser admin_user = new OkapiUser()
}
