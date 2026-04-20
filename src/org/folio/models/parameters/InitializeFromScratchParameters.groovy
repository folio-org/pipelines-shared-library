package org.folio.models.parameters

import org.folio.rest_v2.EntitlementApproach

class InitializeFromScratchParameters {

  EntitlementApproach entitlementApproach
  boolean setBaseUrl = true
  boolean migrate = false

  InitializeFromScratchParameters withEntitlementApproach(EntitlementApproach entitlementApproach) {
    this.entitlementApproach = entitlementApproach
    return this
  }

  InitializeFromScratchParameters withSetBaseUrl(boolean setBaseUrl) {
    this.setBaseUrl = setBaseUrl
    return this
  }

  InitializeFromScratchParameters withMigrate(boolean migrate) {
    this.migrate = migrate
    return this
  }
}