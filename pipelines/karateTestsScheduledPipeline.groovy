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

//                    call job to setup env (https://issues.folio.org/browse/RANCHER-12)
//                    def jobParameters = [
//                        string(name: 'branch', value: params.branch),
//                        string(name: 'threadsCount', value: "4"),
//                        string(name: 'okapiUrl', value: okapiUrl),
//                        string(name: 'tenant', value: tenant),
//                        string(name: 'adminUserName', value: user),
//                        string(name: 'adminPassword', value: password)
//                    ]
//
//                    def jobName = "/Testing/Karate tests"
//                    def karateTestsJob = build job: jobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Run karate tests") {
            steps {
                script {
                    okapiUrl = 'https://ptf-perf-okapi.ci.folio.org'
                    tenant = 'fs09000000'
                    user = 'folio'
                    password = 'folio'

                    def jobParameters = [
                        string(name: 'modules', value: "mod-search"),

                        string(name: 'branch', value: params.branch),
                        string(name: 'threadsCount', value: "4"),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: tenant),
                        string(name: 'adminUserName', value: user),
                        string(name: 'adminPassword', value: password)
                    ]

                    def jobName = "/Testing/Karate tests"
                    def karateTestsJob = build job: jobName, parameters: jobParameters, wait: true, propagate: false

                    copyArtifacts(projectName: jobName, selector: specific(karateTestsJob.number), filter("/teams-assignment.json"))
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
