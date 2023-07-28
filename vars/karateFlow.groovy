import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library
import groovy.json.JsonSlurper

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
    stage('Send in slack test results notifications') {
        script {
            def files_list = findFiles( excludes: '', glob: "**/target/karate-reports_*/karate-summary-json.txt")
            def passedTestsCount = 0
            def failedTestsCount = 0
            files_list.each { test ->
                def json = readJSON file: test.path
                def featureFailed = json['featuresFailed']
                if (featureFailed != 0 ){ failedTestsCount += featureFailed }
                def featurePassed = json['featuresPassed']
                if (featurePassed !=0) {passedTestsCount += featurePassed }
            }
            def totalTestsCount = passedTestsCount + failedTestsCount
//            def passRate = totalTestsCount > 0 ? (passedTestsCount * 100) / totalTestsCount : 100
            def passRate = totalTestsCount > 0 ? String.format("%.1f", (passedTestsCount * 100) / totalTestsCount) : "100.0"
            println ('Failed tests count: ' + failedTestsCount)
            println ('Passed tests count: ' + passedTestsCount)
            println ('Total tests count: ' + totalTestsCount)
            if (currentBuild.result == 'FAILURE' || (passRate != null && passRate < 50)) {
                slackSend(channel: "#rancher_karate_cypress_tests_notif", color: 'danger', message: "Karate tests results: Passed tests: ${passedTests}, Failed tests: ${failedTests}, Pass rate: ${passRate}%")
            }
            else {
                slackSend(channel: "#rancher_karate_cypress_tests_notif", color: 'good', message: "Karate tests results: Passed tests: ${passedTests}, Failed tests: ${failedTests}, Pass rate: ${passRate}%")
            }
        }
    }
}

