package org.folio.models.parameters

import org.folio.models.OkapiTenant

//TODO Switch parameters in Cypress pipelines and move report portal setting to ReportPortal package
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

  String workerLabel

  int numberOfWorkers

  boolean reportPortalUse

  String reportPortalRunType

  String timeout
}
