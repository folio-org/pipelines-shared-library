package org.folio.models.parameters

import org.folio.models.OkapiTenant

//TODO Switch parameters in Cypress pipelines and move report portal setting to ReportPortal package
class CypressTestsParameters {
  String buildName

  String testsSrcBranch

  String parallelExecParameters

  String sequentialExecParameters

  String okapiUrl

  String tenantUrl

  OkapiTenant tenant

  String testrailProjectID

  String testrailRunID

  String workerLabel

  int numberOfWorkers

  boolean reportPortalUse

  String reportPortalProjectName

  String reportPortalProjectId

  String reportPortalRunType

  String timeout
}
