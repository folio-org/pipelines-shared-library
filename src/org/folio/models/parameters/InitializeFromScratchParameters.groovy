package org.folio.models.parameters

import org.folio.rest_v2.EntitlementApproach

/**
 * Carries per-run initialization settings consumed by Eureka.initializeFromScratch.
 * Values are set either explicitly (e.g. from the Jenkins UI) or derived via
 * {@link DependentParametersResolver}. Nullable fields indicate "not explicitly
 * set yet" so downstream layers can fill in the authoritative default.
 */
class InitializeFromScratchParameters {

  EntitlementApproach entitlementApproach
  boolean setBaseUrl = true
  boolean migrate = false

  InitializeFromScratchParameters() {}

  InitializeFromScratchParameters(EntitlementApproach entitlementApproach) {
    this.entitlementApproach = entitlementApproach
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
