package org.folio.models

import com.cloudbees.groovy.cps.NonCPS

class Tenant {
  /** Tenant's identifier. */
  String tenantId

  /** Tenant's name. */
  String tenantName

  /** Description of the tenant. */
  String tenantDescription

  /** User Interface (UI) details for the tenant. */
  TenantUi tenantUi

  /**
   * Chainable setter for tenant UI.
   * @param tenantUi User Interface (UI) details for the tenant.
   * @return The OkapiTenant object.
   */
  Tenant withTenantUi(TenantUi tenantUi) {
    this.tenantUi = tenantUi
    this.tenantUi.tenantId = this.tenantId
    return this
  }

  Map toMap(){
    Map ret = [
      name: tenantName,
      description: tenantDescription
    ]

    if(tenantId.trim())
      ret.put("id", tenantId)

    return ret
  }

  static Tenant getTenantFromContent(Map content){
    return new Tenant(
      tenantId: content.id
      , tenantName: content.name
      , tenantDescription: content.description
    )
  }

  @NonCPS
  @Override
  String toString(){
    return """
      "class_name": "Tenant",
      "tenantId": "$tenantId",
      "tenantName": "$tenantName",
      "tenantDescription": "$tenantDescription"
    """
  }
}
