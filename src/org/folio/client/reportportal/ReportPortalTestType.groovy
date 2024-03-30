package org.folio.client.reportportal

import com.cloudbees.groovy.cps.NonCPS
import org.folio.shared.TestType

enum ReportPortalTestType{
  KARATE(ReportPortalConstants.KARATE_PROJECT_NAME, ReportPortalConstants.KARATE_EXEC_PARAM_TEMPLATE, TestType.KARATE),
  CYPRESS(ReportPortalConstants.CYPRESS_PROJECT_NAME, ReportPortalConstants.CYPRESS_EXEC_PARAM_TEMPLATE, TestType.CYPRESS),
  OTHER("", "", TestType.OTHER)

  final String projectName
  final String execParamTemplate
  final TestType baseType

  ReportPortalTestType(String projectName, String execParamTemplate, TestType type) {
    this.projectName = projectName
    this.baseType = type
    this.execParamTemplate = execParamTemplate
  }

  String reportPortalDashboardURL(){
    return "${ReportPortalConstants.URL}/ui/#${projectName}/dashboard"
  }

  @NonCPS
  static ReportPortalTestType fromType(TestType type) throws Error{
    for(ReportPortalTestType elem: values()){
      if(elem.baseType == type){
        return elem
      }
    }
    throw new Error("Unknown test type")
  }
}
