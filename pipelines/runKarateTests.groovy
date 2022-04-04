@Library('pipelines-shared-library@RANCHER-251') _


import org.folio.karate.results.KarateExecutionResult
import org.folio.karate.results.KarateModuleTestResult
import org.folio.karate.results.KarateTestsResult
import org.folio.karate.teams.KarateTeam
import org.folio.karate.teams.TeamAssignmentParser
import org.jenkinsci.plugins.workflow.libs.Library

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
                        println "Collecting tests execution result from '${karateSummary.path}' file"
                        String[] split = karateSummary.path.split("/")
                        String moduleName = split[split.size() - 4]

                        def contents = readJSON file: karateSummary.path
                        karateTestsResult.addModuleResult(moduleName, contents.featuresPassed, contents.featuresFailed, contents.featuresSkipped)
                    }

                    println karateTestsResult
                }
            }
        }

        stage("Send slack notifications") {
            steps {
                script {
                    def parser = new TeamAssignmentParser(this, "${env.WORKSPACE}/teams-assignment.json")

                    Map<KarateTeam, List<KarateModuleTestResult>> teamResults = [:]
                    def teamByModule = parser.getTeamsByModules()
                    karateTestsResult.getModulesTestResult().values().each { moduleTestResult ->
                        if (teamByModule.containsKey(moduleTestResult.getName())) {
                            def team = teamByModule.get(moduleTestResult.getName())
                            if (!teamResults.containsKey(team)) {
                                teamResults[team] = []
                            }
                            teamResults[team].add(moduleTestResult)
                            println "Module '${moduleTestResult.name}' is assignned to '${team.name}'"
                        } else {
                            println "Module '${moduleTestResult.name}' is not assignned to any team"
                        }
                    }

                    teamResults.each { entry ->
                        def msg = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}"
                        entry.value.each { moduleTestResult ->
                            if (moduleTestResult.getExecutionResult() == KarateExecutionResult.FAIL) {
                                message += "Module '${moduleTestResult.getName()}' has ${moduleTestResult.getFailedCount()} failures of ${moduleTestResult.getTotalCount()}.\n"
                            }
                        }

                        println "We are about to send notif to slack:\n $message"
                        println "Channel: ${entry.key.slackChannel}"
                        slackSend(color: getColor(buildStatus), message: message, channel: "#jenkins-test")
                    }
                }
            }
        }
    }
}

def getColor(buildStatus) {
    if (buildStatus == 'STARTED') {
        '#D4DADF'
    } else if (buildStatus == 'SUCCESS') {
        '#BDFFC3'
    } else if (buildStatus == 'UNSTABLE') {
        '#FFFE89'
    } else {
        '#FF9FA1'
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
