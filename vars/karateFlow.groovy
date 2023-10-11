import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library
import groovy.text.StreamingTemplateEngine
import groovy.json.JsonSlurperClassic
import java.time.*

@Library('pipelines-shared-library') _

def call(params) {
    def res
    def id
    String rp_project = "junit5-integration"
    String rp_host = "https://poc-report-portal.ci.folio.org"
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
    stage("[Report-Portal]") {
        println("Binding config file..")
        try {
            withCredentials([string(credentialsId: 'report-portal-api-key-1', variable: 'api_key')]) {
                String key_path = "${env.WORKSPACE}/testrail-integration/src/main/resources/reportportal.properties"
                String source_tpl = readFile file: key_path
                String url = "https://poc-report-portal.ci.folio.org/api/v1/junit5-integration/launch"
                LinkedHashMap key_data = [rp_key: "${env.api_key}", pr_url: rp_host, rp_project: rp_project]
                writeFile encoding: 'utf-8', file: key_path, text: (new StreamingTemplateEngine().createTemplate(source_tpl).make(key_data)).toString()

                res = sh(returnStdout: true, script: """
                  curl --header "Content-Type: application/json" \
                  --header "Authorization: Bearer ${env.api_key}" \
                  --request POST \
                  --data '{"name":"Test (Jenkins) build number: ${env.BUILD_NUMBER}","description":"Karate scheduled tests.","startTime":"${Instant.now()}","mode":"DEFAULT","attributes":[{"key":"build","value":"${env.BUILD_NUMBER}"}]}' \
                  ${url} """)
                id = new JsonSlurperClassic().parseText("${res}")
                println("Run id: " + id['id'])
                return id
            }
        } catch (Exception e) {
            println("Couldn't create a new run in ReportPortal\nPlease make sure it's online...\nError: ${e.getMessage()}")
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
                if (id['id'] != null) {
                    sh "mvn test -T ${threadsCount} ${modules} -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment} -Drp.launch.uuid=${id['id']}"
                } else {
                    sh "mvn test -T ${threadsCount} ${modules} -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment}"
                }
            }
        }
    }
    stage("[Stop run on Report Portal]") {
        try {
            withCredentials([string(credentialsId: 'report-portal-api-key-1', variable: 'api_key')]) {
                String url = "https://poc-report-portal.ci.folio.org/api/v1/junit5-integration/launch/${id['id']}/finish"
                def res_end = sh(returnStdout: true, script: """
             curl --header "Content-Type: application/json" \
             --header "Authorization: Bearer ${env.api_key}" \
             --request PUT \
             --data '{"endTime":"${Instant.now()}"}' ${url}
             """)
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
            def passedTestsCount = 0
            def failedTestsCount = 0
            files_list.each { test ->
                def json = readJSON file: test.path
                def testsFailed = json['scenariosfailed']
                if (testsFailed != 0) {
                    failedTestsCount += testsFailed
                }
                def testsPassed = json['scenariosPassed']
                if (testsPassed != 0) {
                    passedTestsCount += testsPassed
                }
            }
            def totalTestsCount = passedTestsCount + failedTestsCount
            def passRateInDecimal = totalTestsCount > 0 ? (passedTestsCount * 100) / totalTestsCount : 100
            def passRate = passRateInDecimal.intValue()
            if (currentBuild.result == 'FAILURE' || (passRate != null && passRate < 50)) {
                slackSend(channel: "#rancher_tests_notifications", color: 'danger', message: "Karate tests results: Passed tests: ${passedTestsCount}, Failed tests: ${failedTestsCount}, Pass rate: ${passRate}%")
            } else {
                slackSend(channel: "#rancher_tests_notifications", color: 'good', message: "Karate tests results: Passed tests: ${passedTestsCount}, Failed tests: ${failedTestsCount}, Pass rate: ${passRate}%")
            }
        }
    }
}

