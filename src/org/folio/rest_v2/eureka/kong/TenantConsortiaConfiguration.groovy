package org.folio.rest_v2.eureka.kong

class TenantConsortiaConfiguration {
  String consortiaTenantUuid
  String centralTenantId

  TenantConsortiaConfiguration(String consortiaTenantUuid, String centralTenantId) {
    this.centralTenantId = centralTenantId
    this.consortiaTenantUuid = consortiaTenantUuid
  }
}
