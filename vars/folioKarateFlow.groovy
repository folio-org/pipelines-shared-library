import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.jenkins.PodTemplates
import org.folio.models.parameters.KarateTestsParameters
import org.folio.testing.TestType
import org.folio.testing.karate.results.KarateRunExecutionSummary
import org.folio.testing.teams.TeamAssignment
import org.folio.utilities.Logger
import org.folio.utilities.RestClient

import java.time.Instant

KarateRunExecutionSummary call(KarateTestsParameters args) {
  Logger logger = new Logger(this, 'Karate flow')
  PodTemplates podTemplates = new PodTemplates(this, true)
  KarateRunExecutionSummary karateTestsExecutionSummary

  podTemplates.javaKarateAgent(args.javaVerson) {
    dir('folio-integration-tests') {
      stage('[Git] Checkout folio-integration-tests repo') {
        checkout(scmGit(
          branches: [[name: "*/${args.gitBranch}"]],
          extensions: [cloneOption(depth: 10, noTags: true, reference: '', shallow: true),
                       cleanBeforeCheckout(deleteUntrackedNestedRepositories: true)],
          userRemoteConfigs: [[credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                               url          : "${Constants.FOLIO_GITHUB_URL}/folio-integration-tests.git"]]))
      }

      stage('Build karate config') {
        args.teamAssignment = new TeamAssignment(readJSON(file: "teams-assignment.json"))
        List files = findFiles(glob: '**/karate-config.js')
        files.each { file ->
          logger.info("Updating file: ${file.path}")
          writeFile(file: file.path, text: karateTestUtils.renderKarateConfig(readFile(file.path), args))
        }
      }

      if (args.reportPortalProjectName) {
        stage('[ReportPortal] Start run') {
          args.reportPortalProjectId = startReportPortalRun(args.reportPortalProjectName)
        }
      }

      stage('[Maven] Execute karate tests') {
        timeout(time: args.timeout, unit: 'HOURS') {
          container('java') {
            withMaven(jdk: args.javaToolName, maven: args.mavenToolName,
              mavenLocalRepo: "${podTemplates.WORKING_DIR}/.m2/repository",
              traceability: true,
              options: [artifactsPublisher(disabled: true)]) {
              logger.debug(sh(returnStdout: true, script: 'echo $JAVA_HOME').trim())
              String modules = args.modulesToTest ? "-pl common,testrail-integration," + args.modulesToTest : args.modulesToTest
              catchError(stageResult: 'FAILURE') {
                String execParams = "-DfailIfNoTests=false -DargLine=-Dkarate.env=${args.karateConfig}"

                execParams = args.lsdi ? "$execParams -pl data-import-large-scale-tests -am -DskipTests=false" : "$execParams -T ${args.threadsCount} ${modules}"

                execParams = args.reportPortalProjectId ? "$execParams -Drp.launch.uuid=${args.reportPortalProjectId}" : execParams

                sh "mvn test $execParams"
              }
            }
          }
        }

        stage('[Report] Publish results') {
          cucumber(buildStatus: 'UNSTABLE',
            fileIncludePattern: '**/target/karate-reports*/*.json',
            sortingMethod: 'ALPHABETICAL')

          junit(testResults: '**/target/karate-reports*/*.xml')
        }

        if (args.reportPortalProjectName && args.reportPortalProjectId) {
          stage("[ReportPortal] Finish run") {
            stopReportPortalRun(args.reportPortalProjectName, args.reportPortalProjectId)
          }
        }

        stage('[Report] Analyze results') {
          karateTestsExecutionSummary = karateTestUtils.collectTestsResults("**/target/karate-reports*/karate-summary-json.txt")
          karateTestUtils.attachCucumberReports(karateTestsExecutionSummary)
        }

        stage('[Archive] Archive artifacts') {
          zip zipFile: "cucumber.zip", glob: "**/target/karate-reports*/*.json"
          zip zipFile: "junit.zip", glob: "**/target/karate-reports*/*.xml"
          zip zipFile: "karate-summary.zip", glob: "**/target/karate-reports*/karate-summary-json.txt"

          archiveArtifacts allowEmptyArchive: true, artifacts: "cucumber.zip", fingerprint: true, defaultExcludes: false
          archiveArtifacts allowEmptyArchive: true, artifacts: "junit.zip", fingerprint: true, defaultExcludes: false
          archiveArtifacts allowEmptyArchive: true, artifacts: "karate-summary.zip", fingerprint: true, defaultExcludes: false
          archiveArtifacts allowEmptyArchive: true, artifacts: "teams-assignment.json", fingerprint: true, defaultExcludes: false
        }

        if (args.syncWithJira) {
          stage('[Jira] Sync issues') {
            karateTestUtils.syncJiraIssues(karateTestsExecutionSummary, args.teamAssignment)
          }
        }

        if (args.sendSlackNotification) {
          stage('[Slack] Send notification') {
            slackSend(attachments: folioSlackNotificationUtils
              .renderBuildAndTestResultMessage(
                TestType.KARATE
                , karateTestsExecutionSummary
                , ""
                , true
                , "${env.BUILD_URL}cucumber-html-reports/overview-features.html"
              )
              , channel: "#rancher_tests_notifications")
          }
        }

        if (args.sendTeamsSlackNotification) {
          stage("Send slack notifications to teams") {
            folioSlackNotificationUtils.renderTeamsTestResultMessages(
              TestType.KARATE
              , karateTestsExecutionSummary
              , args.teamAssignment
              , ""
              , true
              , "${env.BUILD_URL}cucumber-html-reports/overview-features.html")
              .each {
                slackSend(attachments: it.value, channel: it.key.getSlackChannel())
              }
          }
        }
      }
      return karateTestsExecutionSummary
    }
  }
}

String startReportPortalRun(String projectName) {
  try {
    String url = "${Constants.REPORT_PORTAL_API_URL}/${projectName}/launch"
    Map headers = ['Content-Type': 'application/json']
    String reportPortalPropertiesPath = './testrail-integration/src/main/resources/reportportal.properties'
    String reportPortalPropertiesTpl = readFile file: reportPortalPropertiesPath
    withCredentials([string(credentialsId: Constants.REPORT_PORTAL_API_KEY_ID, variable: 'API_KEY')]) {
      headers['Authorization'] = 'Bearer ' + API_KEY
      Map reportPortalPropertiesData = [rp_key    : API_KEY,
                                        rp_url    : Constants.REPORT_PORTAL_URL,
                                        rp_project: projectName]
      writeFile encoding: 'utf-8',
        file: reportPortalPropertiesPath,
        text: (new StreamingTemplateEngine().createTemplate(reportPortalPropertiesTpl).make(reportPortalPropertiesData)).toString()
    }
    Map body = [name       : "${env.JOB_BASE_NAME}: ${env.BUILD_NUMBER}",
                description: "'${env.JOB_NAME}' Jenkins job. Build URL: ${env.BUILD_URL}",
                startTime  : "${Instant.now()}",
                mode       : "DEFAULT",
                attributes : [[key: "build", value: "${env.BUILD_NUMBER}"]]
    ]

    return new RestClient(this).post(url, body, headers).body['id']
  } catch (Exception e) {
    println("Not able to start Report Portal run. Error: " + e.getMessage())
  }
}

void stopReportPortalRun(String projectName, String runId) {
  try {
    String url = "${Constants.REPORT_PORTAL_API_URL}/${projectName}/launch/${runId}/finish"
    Map headers = ['Content-Type': 'application/json']
    withCredentials([string(credentialsId: Constants.REPORT_PORTAL_API_KEY_ID, variable: 'API_KEY')]) {
      headers['Authorization'] = 'Bearer ' + API_KEY
    }
    Map body = [endTime: "${Instant.now()}"]
    println(new RestClient(this).put(url, body, headers).body)
  } catch (Exception e) {
    println("Not able to stop Report Portal run. Error: " + e.getMessage())
  }
}
