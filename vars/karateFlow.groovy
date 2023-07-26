import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-859') _

def call(params) {
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
                sh "mvn test -T ${threadsCount} ${modules} -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment}"
            }
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
    stage('Slack Notification Test PassRate') {
            script {
                def passedTests = sh(returnStdout: true, script: "grep -Eo '\"status\":\"PASSED\"' **/target/karate-reports*/*.json | wc -l").trim().toInteger()
                def failedTests = sh(returnStdout: true, script: "grep -Eo '\"status\":\"FAILED\"' **/target/karate-reports*/*.json | wc -l").trim().toInteger()
                def totalTests = passedTests + failedTests
                def passRate = totalTests > 0 ? (passedTests * 100) / totalTests : 100

                echo "Passed tests: ${passedTests}"
                echo "Failed tests: ${failedTests}"
                echo "Total tests: ${totalTests}"
                echo "Pass rate: ${passRate}%"

                if (currentBuild.result == 'FAILURE' || passRate < 50) {
                    slackSend(channel: "#kitfox-shadow", color: 'danger', message: "Karate tests results: Passed tests: ${passedTests}, Failed tests: ${failedTests}, Pass rate: ${passRate}%")
            }
        }
    }
}
