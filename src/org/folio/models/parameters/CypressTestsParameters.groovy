package org.folio.models.parameters

import org.folio.models.OkapiTenant

/**
 * Represents the parameters required to execute Cypress tests.
 *
 * This class holds the necessary configuration for running Cypress tests,
 * including the CI build ID, test source branch, browser settings, and
 * optional reporting configurations.
 */
class CypressTestsParameters implements Cloneable {
  String ciBuildId
  String testsSrcBranch
  String browserName
  String execParameters
  String okapiUrl
  String tenantUrl
  OkapiTenant tenant
  String testrailProjectID = ''
  String testrailRunID = ''
  String workerLabel = 'cypress'
  int numberOfWorkers = 1
//  boolean reportPortalUse = false
//  String reportPortalRunType = ''
  String timeout = '480' // Minutes

  @Override
  Object clone() {
    return super.clone()
  }
}
