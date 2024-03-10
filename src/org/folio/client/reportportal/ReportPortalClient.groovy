package org.folio.client.reportportal

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.text.StreamingTemplateEngine
import org.folio.shared.TestType
import org.folio.utilities.RestClient

import java.time.Instant

class ReportPortalClient {

  private enum ReportPortalTestType{
    KARATE(ReportPortalConstants.KARATE_PROJECT_NAME, ReportPortalConstants.KARATE_EXEC_PARAM_TEMPLATE, TestType.KARATE),
    CYPRESS(ReportPortalConstants.CYPRESS_PROJECT_NAME, ReportPortalConstants.CYPRESS_EXEC_PARAM_TEMPLATE, TestType.CYPRESS)

    private String projectName
    private String execParamTemplate
    private TestType baseType

    ReportPortalTestType(String projectName, String execParamTemplate, TestType type) {
      this.projectName = projectName
      this.baseType = type
      this.execParamTemplate = execParamTemplate
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

  private ReportPortalTestType testType
  private def pipeline
  private def buildNumber
  private def workspace
  def launchID = null

  ReportPortalClient(def pipeline, TestType testType, def buildNumber, def workspace) throws Error{
    this.pipeline = pipeline
    this.testType = ReportPortalTestType.fromType(testType)
    this.buildNumber = buildNumber
    this.workspace = workspace
  }

  def launch() throws Error{
    pipeline.withCredentials([pipeline.string(credentialsId: ReportPortalConstants.CREDENTIALS_ID, variable: 'apiKey')]) {
      String url = "${ReportPortalConstants.API_URL}/${testType.projectName}/launch"

      tuneWorkspace(pipeline.apiKey)

      Map headers = [
        "Content-type" : "application/json",
        "Authorization": "Bearer ${pipeline.apiKey}"
      ]

      String body = JsonOutput.toJson([
        name       : "Test (Jenkins) build number: ${buildNumber}",
        description: "${testType.name()} scheduled tests",
        startTime  : "${Instant.now()}",
        mode       : "DEFAULT",
        attributes : [[key: "build", value: "${buildNumber}"]]
      ])

      def res = new RestClient(this).post(url, body, headers)
      launchID = res.body['id']
      return launchID
    }
  }

  String getExecParams() throws Error{
    if(!launchID) return ""

    withCredentials([string(credentialsId: ReportPortalConstants.CREDENTIALS_ID, variable: 'apiKey')]) {
      if (testType.equals(ReportPortalTestType.KARATE)) {
        LinkedHashMap bind = [
          "api_url"     : ReportPortalConstants.API_URL,
          "api_key"     : apiKey,
          "project_name": testType.projectName,
          "description" : "${testType.name()} scheduled tests",
          "launch_id"   : launchID
        ]

        return (new StreamingTemplateEngine()
                    .createTemplate(testType.execParamTemplate)
                    .make(bind)
               ).toString()
      }
    }
  }

  def launchFinish() throws Error{
    withCredentials([string(credentialsId: ReportPortalConstants.CREDENTIALS_ID, variable: 'apiKey')]) {
      String url = "${ReportPortalConstants.API_URL}/${testType.projectName}/launch/${launchID}/finish"

      Map headers = [
        "Content-Type" : "application/json",
        "Authorization": "Bearer ${apiKey}"
      ]
      String body = JsonOutput.toJson([
        endTime: "${Instant.now()}"
      ])
      def res_end = new RestClient(this).put(url, body, headers)
      return res_end
    }
  }

  private tuneWorkspace(def apiKey){
    if(testType == ReportPortalTestType.KARATE){
      String credFilePath = "${workspace}/${ReportPortalConstants.KARATE_CRED_TEMPLATE_FILE_PATH}"

      String credFileSource = readFile file: credFilePath

      LinkedHashMap data = [rp_key: "${apiKey}",
                            rp_url: ReportPortalConstants.URL,
                            rp_project: ReportPortalConstants.KARATE_PROJECT_NAME]

      writeFile encoding: 'utf-8', file: credFilePath,
        text: (new StreamingTemplateEngine().createTemplate(credFileSource).make(data)).toString()
    }
  }
}
