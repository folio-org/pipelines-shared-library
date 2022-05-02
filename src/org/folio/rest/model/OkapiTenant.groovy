package org.folio.rest.model

class OkapiTenant implements Serializable {
    String id
    String name
    String description
    Map parameters
    OkapiUser admin_user = new OkapiUser()
}
