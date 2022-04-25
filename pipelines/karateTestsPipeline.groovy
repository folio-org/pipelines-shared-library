import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library') _

def karateEnvironment = "jenkins"

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'modules', defaultValue: '', description: 'Comma separated modules list to build(no spaces). Leave empty to launch all.')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
        string(name: 'okapiUrl', defaultValue: 'https://ptf-perf-okapi.ci.folio.org', description: 'Target environment OKAPI URL')
        string(name: 'tenant', defaultValue: 'fs09000000', description: 'Tenant name for tests execution')
        string(name: 'adminUserName', defaultValue: 'folio', description: 'Admin user name')
        password(name: 'adminPassword', defaultValue: 'folio', description: 'Admin user password')
    }

    stages {
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
                    def jenkinsKarateConfigContents = getKarateConfig()
                    def files = findFiles(glob: '**/karate-config.js')

                    files.each { file ->
                        String path = file.path.replaceAll("\\\\", "/")
                        def folderPath = path.substring(0, path.lastIndexOf("/"))

                        echo "Creating file ${folderPath}/karate-config-${karateEnvironment}.js"
                        writeFile file: "${folderPath}/karate-config-${karateEnvironment}.js", text: jenkinsKarateConfigContents
                    }
                }
            }
        }

        stage('Run karate tests') {
            steps {
                script {
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
                        fileIncludePattern: "**/target/karate-reports*/*.json"

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
}

def getKarateConfig() {
    """
function fn() {
    var config = {
        baseUrl: '${params.okapiUrl}',
        admin: {
            tenant: '${params.tenant}',
            name: '${params.adminUserName}',
            password: '${params.adminPassword}'
        }
    }

    return config;
}
"""
}
