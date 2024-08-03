package org.folio.models

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
   * @return The Tenant object.
   */
  void setTenantUi(TenantUi tenantUi) {
    this.tenantUi = tenantUi
    this.tenantUi.tenantId = this.tenantId
  }

  static Tenant getTenantFromJson(def json){
    return new Tenant(
      tenantId: json.id
      , tenantName: json.name
      , tenantDescription: json.description
    )
  }
}
