package org.folio.client.reportportal

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.text.StreamingTemplateEngine
import org.folio.shared.TestType
import org.folio.utilities.RestClient

import java.time.Instant

class ReportPortalClient {
  private ReportPortalTestType testType
  private def pipeline
  private def buildName
  private def buildNumber
  private def workspace
  private def launchID = null

  ReportPortalClient(def pipeline, TestType testType, def buildName, def buildNumber, def workspace) throws Error{
    this.pipeline = pipeline
    this.testType = ReportPortalTestType.fromType(testType)
    this.buildName = buildName
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
        name       : buildName,
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
    if(launchID == null) return ""

    pipeline.withCredentials([pipeline.string(credentialsId: ReportPortalConstants.CREDENTIALS_ID, variable: 'apiKey')]) {
      LinkedHashMap bind = [
        "api_url"     : ReportPortalConstants.API_URL,
        "api_key"     : pipeline.apiKey,
        "project_name": testType.projectName,
        "description" : "${testType.name()} scheduled tests",
        "launch_id"   : launchID,
        "launch_name" : buildName,
        "attributes"  : [key: "build", value: "${buildNumber}"]
      ]

      return (new StreamingTemplateEngine()
                  .createTemplate(testType.execParamTemplate)
                  .make(bind)
             ).toString()
    }
  }

  def launchFinish() throws Error{
    pipeline.withCredentials([pipeline.string(credentialsId: ReportPortalConstants.CREDENTIALS_ID, variable: 'apiKey')]) {
      String url = "${ReportPortalConstants.API_URL}/${testType.projectName}/launch/${launchID}/finish"

      Map headers = [
        "Content-Type" : "application/json",
        "Authorization": "Bearer ${pipeline.apiKey}"
      ]
      String body = JsonOutput.toJson([
        endTime: "${Instant.now()}"
      ])
      def res_end = new RestClient(this).put(url, body, headers)

      launchID = null

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
