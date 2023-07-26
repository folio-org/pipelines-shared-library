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
//            def dir = new File('**/target/karate-reports*')
            List files_list = findFiles excludes: '', glob: '**/target/karate-reports*/karate-summary-json.txt'
//            def files = dir.listFiles()
//            files.each { file ->
//                if (file.name.contains('.json')){
//                    files_list += file.name}
//            }
            def positive_counter = 0
            def negative_counter = 0
//            def totalTestCount = 0
            files_list.each { test ->
                def json = new JsonSlurper().parseText(new File(test).text)
                def temp_result = json[0]['stats']['failed']
                if (temp_result != 0 ){ negative_counter += temp_result }
                def temp_result1= json[0]['stats']['passed']
                if (temp_result1 !=0) {positive_counter += temp_result1 }
            }
            println ('Failed tests count: ' + negative_counter)
            println ('Passed tests count: ' + positive_counter)
            println ('Total tests count: ' + (positive_counter + negative_counter))

            slackSend(
                channel: '#kitfox-shadow',
                color: 'danger',
                message: "Karate Test Results: Passed tests: ${positive_counter}, Failed tests: ${negative_counter}"
            )
        }
    }
}

