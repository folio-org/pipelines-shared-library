@Library('pipelines-shared-library@RANCHER-251') _


import org.folio.karate.results.KarateTestsResult
import org.folio.karate.teams.TeamAssignment
import org.jenkinsci.plugins.workflow.libs.Library

def okapiUrl, tenant, user, password
KarateTestsResult karateTestsResult

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'RANCHER-239', description: 'Karate tests repository branch to checkout')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
    }

    stages {
        stage("Create environment") {
            steps {
                script {
                    okapiUrl = 'https://ptf-perf-okapi.ci.folio.org'
                    tenant = 'fs09000000'
                    user = 'folio'
                    password = 'folio'
                    string(name: 'branch', defaultValue: 'RANCHER-239', description: 'Karate tests repository branch to checkout')
                    string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
                    string(name: 'okapiUrl', defaultValue: 'https://ptf-perf-okapi.ci.folio.org', description: 'Target environment OKAPI URL')
                    string(name: 'tenant', defaultValue: 'fs09000000', description: 'Tenant name for tests execution')
                    string(name: 'adminUserName', defaultValue: 'folio', description: 'Admin user name')
                    password(name: 'adminPassword', defaultValue: 'folio', description: 'Admin user password')

                    def jobParameters = [
                        string(name: 'branch', value: params.branch),
                        string(name: 'threadsCount', value: 4),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: tenant),
                        string(name: 'adminUserName', value: user),
                        string(name: 'adminPassword', value: password)
                    ]

                    build job: "/Testing/job/Karate%20tests", parameters: jobParameters, wait: true, propagate: true
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
                    karateTestsResult = karateTestUtils.collectTestsResults()
                }
            }
        }

        stage("Send slack notifications") {
            steps {
                script {
                    def jsonContents = readJSON file: "${env.WORKSPACE}/teams-assignment.json"
                    def teamAssignment = new TeamAssignment(jsonContents)

                    karateTestUtils.sendSlackNotification(karateTestsResult, teamAssignment)
                }
            }
        }
    }
}
