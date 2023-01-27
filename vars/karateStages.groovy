import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-608') _

def call(params) {
    stage("Checkout") {
        steps {
            script {
                sshagent(credentials: [Constants.GITHUB_CREDENTIALS_ID]) {
                    checkout([
                        $class           : 'GitSCM',
                        branches         : [[name: "*/${params.branch}"]],
                        extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
                                                              disableSubmodules  : false,
                                                              parentCredentials  : false,
                                                              recursiveSubmodules: true,
                                                              reference          : '',
                                                              trackingSubmodules : false]],
                        userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/folio-integration-tests.git"]]
                    ])
                }
            }
        }
    }

    stage("Build karate config") {
        steps {
            script {
                def files = findFiles(glob: '**/karate-config.js')
                files.each { file ->
                    echo "Updating file ${file.path}"
                    writeFile file: file.path, text: karateTestUtils.renderKarateConfig(readFile(file.path), params)
                }

            }
        }
    }

    stage('Run karate tests') {
        steps {
            script {
                def karateEnvironment = "folio-testing-karate"
                withMaven(
                    jdk: 'openjdk-11-jenkins-slave-all',
                    maven: 'maven3-jenkins-slave-all',
                    mavenSettingsConfig: 'folioci-maven-settings'
                ) {
                    def modules = ""
                    if (params.modules) {
                        modules = "-pl common,testrail-integration," + params.modules
                    }
                    sh "mvn test -T ${threadsCount} ${modules} -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment}"
                }
            }
        }
    }

    stage('Publish tests report') {
        steps {
            script {
                cucumber buildStatus: "UNSTABLE",
                    fileIncludePattern: "**/target/karate-reports*/*.json",
                    sortingMethod: "ALPHABETICAL"

                junit testResults: '**/target/karate-reports*/*.xml'
            }
        }
    }

    stage('Archive artifacts') {
        steps {
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
    }
}
