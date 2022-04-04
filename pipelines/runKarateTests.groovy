@Library('pipelines-shared-library@RANCHER-251') _

import org.jenkinsci.plugins.workflow.libs.Library
import org.folio.karate.KarateTestsResult

def karateEnvironment = "jenkins"
KarateTestsResult karateTestsResult = new KarateTestsResult()

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'RANCHER-239', description: 'Karate tests repository branch to checkout')
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
                    sshagent(credentials: ['11657186-f4d4-4099-ab72-2a32e023cced']) {
                        checkout([
                            $class           : 'GitSCM',
                            branches         : [[name: "*/${params.branch}"]],
                            extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
                                                                  disableSubmodules  : false,
                                                                  parentCredentials  : false,
                                                                  recursiveSubmodules: true,
                                                                  reference          : '',
                                                                  trackingSubmodules : false]],
                            userRemoteConfigs: [[url: 'https://github.com/folio-org/folio-integration-tests.git']]
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
                        sh "mvn test -T ${threadsCount} -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment} -pl common,testrail-integration,mod-search,mod-quick-marc"
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

        stage("Collect execution results") {
            steps {
                script {
                    def karateSummaries = findFiles(glob: '**/target/karate-reports*/karate-summary-json.txt')
                    karateSummaries.each { karateSummary ->
                        String[] split = karateSummary.path.split("/")
                        String moduleName = split[split.size() - 4]
                        println "Collecting tests execution result for '${moduleName}' module"

                        def contents = readJSON file: karateSummary.path
                        karateTestsResult.addModuleResult(moduleName, contents.featuresPassed, contents.featuresFailed, contents.featuresSkipped)
                    }

                    echo karateTestsResult
                }
            }
        }

        stage("Send slack notifications") {
            steps {
                script {
                    def testsMapping = readJSON file: "${env.WORKSPACE}/teams-assignment.json"

                    echo testsMapping
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
