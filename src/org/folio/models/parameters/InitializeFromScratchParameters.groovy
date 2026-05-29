package org.folio.models.parameters

import org.folio.rest_v2.EntitlementApproach
import org.folio.rest_v2.FolioRelease

class InitializeFromScratchParameters {

  Set<String> modifiedFields = [] as Set

  EntitlementApproach entitlementApproach
  boolean setBaseUrl = true
  boolean migrate = false

  InitializeFromScratchParameters withEntitlementApproach(EntitlementApproach entitlementApproach) {
    this.entitlementApproach = entitlementApproach
    this.modifiedFields << 'entitlementApproach'
    return this
  }

  InitializeFromScratchParameters withSetBaseUrl(boolean setBaseUrl) {
    this.setBaseUrl = setBaseUrl
    this.modifiedFields << 'setBaseUrl'
    return this
  }

  InitializeFromScratchParameters withMigrate(boolean migrate) {
    this.migrate = migrate
    this.modifiedFields << 'migrate'
    return this
  }

  InitializeFromScratchParameters applyDefaults(FolioRelease releaseType) {
    if (releaseType == null) return this

    if (!modifiedFields.contains('entitlementApproach'))
      this.entitlementApproach = (releaseType == FolioRelease.SUNFLOWER) ? EntitlementApproach.CREATE : EntitlementApproach.STATE

    if (!modifiedFields.contains('setBaseUrl'))
      this.setBaseUrl = (releaseType != FolioRelease.SUNFLOWER)

    return this
  }
}
