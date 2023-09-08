import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library') _

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
            // export and collect karate tests results
            def files_list = findFiles( excludes: '', glob: "**/target/karate-reports*/karate-summary-json.txt")
            def passedTestsCount = 0
            def failedTestsCount = 0
            files_list.each { test ->
                def json = readJSON file: test.path
                def testsFailed = json['scenariosfailed']
                if (testsFailed != 0 ){ failedTestsCount += testsFailed }
                def testsPassed = json['scenariosPassed']
                if (testsPassed !=0) { passedTestsCount += testsPassed }
            }
            def totalTestsCount = passedTestsCount + failedTestsCount
            def passRateInDecimal = totalTestsCount > 0 ? (passedTestsCount * 100) / totalTestsCount : 100
            def passRate = passRateInDecimal.intValue()
            if (currentBuild.result == 'FAILURE' || (passRate != null && passRate < 50)) {
                slackSend(channel: "#rancher_tests_notifications", color: 'danger', message: "Karate tests results: Passed tests: ${passedTestsCount}, Failed tests: ${failedTestsCount}, Pass rate: ${passRate}%")
            }
            else {
                slackSend(channel: "#rancher_tests_notifications", color: 'good', message: "Karate tests results: Passed tests: ${passedTestsCount}, Failed tests: ${failedTestsCount}, Pass rate: ${passRate}%")
            }
        }
    }
}

