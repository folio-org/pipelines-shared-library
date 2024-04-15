import groovy.json.JsonOutput
import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.shared.TestType
import org.folio.utilities.RestClient
import org.jenkinsci.plugins.workflow.libs.Library

import java.time.Instant

@Library('pipelines-shared-library@RANCHER-741-Jenkins-Enhancements') _

def call(params) {
  def id
  stage("Checkout") {
    script {
      sshagent(credentials: [Constants.GITHUB_CREDENTIALS_ID]) {
        checkout([$class           : 'GitSCM',
                  branches         : [[name: "*/${params.branch}"]],
                  extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
                                                        disableSubmodules  : false,
                                                        parentCredentials  : false,
                                                        recursiveSubmodules: true,
                                                        reference          : '',
                                                        trackingSubmodules : false]],
                  userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/folio-integration-tests.git"]]])
      }
    }
  }

  stage("Build karate config") {
    script {
      def files = findFiles(glob: '**/karate-config.js')
      files.each { file ->
        echo "Updating file ${file.path}"
        writeFile file: file.path, text: karateTestUtils.renderKarateConfig(readFile(file.path), params)
      }
    }
  }
  stage('[ReportPortal config bind & Run start]') {
    try {
      withCredentials([string(credentialsId: 'report-portal-api-key-1', variable: 'api_key')]) {
        String url = "https://poc-report-portal.ci.folio.org/api/v1/junit5-integration/launch"
        String key_path = "${env.WORKSPACE}/testrail-integration/src/main/resources/reportportal.properties"
        String source_tpl = readFile file: key_path
        LinkedHashMap key_data = [rp_key: "${env.api_key}", rp_url: "https://poc-report-portal.ci.folio.org", rp_project: "junit5-integration"]
        writeFile encoding: 'utf-8', file: key_path, text: (new StreamingTemplateEngine().createTemplate(source_tpl).make(key_data)).toString()
        Map headers = [
          "Content-type" : "application/json",
          "Authorization": "Bearer ${env.api_key}"
        ]
        String body = JsonOutput.toJson([
          name       : "Test (Jenkins) build number: ${env.BUILD_NUMBER}",
          description: "Karate scheduled tests",
          startTime  : "${Instant.now()}",
          mode       : "DEFAULT",
          attributes : [[key: "build", value: "${env.BUILD_NUMBER}"]]
        ])
        def res = new RestClient(this).post(url, body, headers)
        id = res.body['id']
        println("${id}")
      }
    } catch (Exception e) {
      println("Error: " + e.getMessage())
    }
  }
  stage('Run karate tests') {
    script {
      def karateEnvironment = "folio-testing-karate"
      withMaven(jdk: 'openjdk-17-jenkins-slave-all',
        maven: 'maven3-jenkins-slave-all',
        mavenSettingsConfig: 'folioci-maven-settings') {
        def modules = ""
        if (params.modules) {
          modules = "-pl common,testrail-integration," + params.modules
        }
        sh 'echo JAVA_HOME=${JAVA_HOME}'
        sh 'ls ${JAVA_HOME}/bin'
        if (id) {
          sh "mvn test -T ${threadsCount} ${modules} -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment} -Drp.launch.uuid=${id}"
        } else {
          sh "mvn test -T ${threadsCount} ${modules} -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment}"
        }
      }
    }
  }
  stage("[ReportPortal Run stop]") {
    try {
      withCredentials([string(credentialsId: 'report-portal-api-key-1', variable: 'api_key')]) {
        String url = "https://poc-report-portal.ci.folio.org/api/v1/junit5-integration/launch/${id}/finish"
        Map headers = [
          "Content-Type" : "application/json",
          "Authorization": "Bearer ${env.api_key}"
        ]
        String body = JsonOutput.toJson([
          endTime: "${Instant.now()}"
        ])
        def res_end = new RestClient(this).put(url, body, headers)
        println("${res_end}")
      }
    } catch (Exception e) {
      println("Couldn't stop run in ReportPortal\nError: ${e.getMessage()}")
    }
  }
  stage('Publish tests report') {
    script {
      cucumber buildStatus: "UNSTABLE",
        fileIncludePattern: "**/target/karate-reports*/*.json",
        sortingMethod: "ALPHABETICAL"

      junit testResults: '**/target/karate-reports*/*.xml'
    }
  }

  stage('Archive artifacts') {
    script {
      // archive artifacts for upstream job
      if (currentBuild.getBuildCauses('org.jenkinsci.plugins.workflow.support.steps.build.BuildUpstreamCause')) {
        zip zipFile: "cucumber.zip", glob: "**/target/karate-reports*/*.json"
        zip zipFile: "junit.zip", glob: "**/target/karate-reports*/*.xml"
        zip zipFile: "karate-summary.zip", glob: "**/target/karate-reports*/karate-summary-json.txt"

        archiveArtifacts allowEmptyArchive: true, artifacts: "cucumber.zip", fingerprint: true, defaultExcludes: false
        archiveArtifacts allowEmptyArchive: true, artifacts: "junit.zip", fingerprint: true, defaultExcludes: false
        archiveArtifacts allowEmptyArchive: true, artifacts: "karate-summary.zip", fingerprint: true, defaultExcludes: false
        archiveArtifacts allowEmptyArchive: true, artifacts: "teams-assignment.json", fingerprint: true, defaultExcludes: false
      }
    }
  }

  stage('Send in slack test results notifications') {
    script {
      // export and collect karate tests results
      def files_list = findFiles(excludes: '', glob: "**/target/karate-reports*/karate-summary-json.txt")

      LinkedHashMap<String, Integer> statusCounts = [failed: 0, passed: 0, broken: 0]
      files_list.each { test ->
        def json = readJSON file: test.path
        def testsFailed = json['scenariosfailed']
        if (testsFailed != 0) {
          statusCounts.failed += testsFailed
        }
        def testsPassed = json['scenariosPassed']
        if (testsPassed != 0) {
          statusCounts.passed += testsPassed
        }
      }

      slackSend(attachments: folioSlackNotificationUtils
                              .renderSlackTestResultMessage(
                                TestType.KARATE
                                , statusCounts
                                , ""
                                , true
                                , "${env.BUILD_URL}cucumber-html-reports/overview-features.html"
                              )
                , channel: "#rancher_tests_notifications")
    }
  }
}

