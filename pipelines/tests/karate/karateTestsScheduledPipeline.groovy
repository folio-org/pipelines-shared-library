package tests.karate

@Library('pipelines-shared-library@RANCHER-939') _

import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.TeamAssignment
import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library

def clusterName = "folio-testing"
def projectName = "karate"
def folio_repository = "platform-complete"
def folio_branch = "snapshot"
def okapiUrl = "https://${clusterName}-${projectName}-okapi.ci.folio.org"
def edgeUrl = "https://${clusterName}-${projectName}-edge.ci.folio.org"
def prototypeTenant = "consortium"

def spinUpEnvironmentJobName = "/folioRancher/folioNamespaceTools/createNamespaceFromBranch"
def destroyEnvironmentJobName = "/folioRancher/folioNamespaceTools/deleteNamespace"
def spinUpEnvironmentJob
def tearDownEnvironmentJob

KarateTestsExecutionSummary karateTestsExecutionSummary
def teamAssignment

Tools tools = new Tools(this)
List<String> versions = tools.eval(jobsParameters.getOkapiVersions(), ["folio_repository": folio_repository, "folio_branch": folio_branch])
String okapiVersion = versions[0] //versions.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: [VersionConstants.MASTER_BRANCH]))[0]

pipeline {
    try {

        agent { label 'jenkins-agent-java17' }

        triggers {
            //cron('H 3 * * *')
        }

        options {
            disableConcurrentBuilds()
        }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
    }

    stages {
        try {
        stage("Check environment") {
            steps {
                script {
                    def jobParameters = getDestroyEnvironmentJobParameters(clusterName, projectName)

                    tearDownEnvironmentJob = build job: destroyEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }
        } catch (Exception new_ex)
                { println('Existing env: ' + new_ex) }

        stage("Create environment") {
            steps {
                script {
                    def jobParameters = getEnvironmentJobParameters('apply', okapiVersion, clusterName,
                        projectName, prototypeTenant, folio_repository, folio_branch)

                    spinUpEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Start tests") {
            when {
                expression {
                    spinUpEnvironmentJob.result == 'SUCCESS'
                }
            }
            steps {
                script {
                    def jobParameters = [
                        branch         : params.branch,
                        threadsCount   : "4",
                        modules        : "",
                        okapiUrl       : okapiUrl,
                        edgeUrl        : edgeUrl,
                        tenant         : 'supertenant',
                        adminUserName  : 'super_admin',
                        adminPassword  : 'admin',
                        prototypeTenant: prototypeTenant
                    ]

                    // Disable temporary, check tests results without sleep
                    // sleep time: 60, unit: 'MINUTES'
                    karateFlow(jobParameters)
                }
            }
        }

        stage("Parallel") {
            parallel {
                stage("Destroy environment") {
                    steps {
                        script {
                            def jobParameters = getDestroyEnvironmentJobParameters(clusterName, projectName)

                            tearDownEnvironmentJob = build job: destroyEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                        }
                    }
                }

                stage("Collect test results") {
                    when {
                        expression {
                            spinUpEnvironmentJob.result == 'SUCCESS'
                        }
                    }
                    stages {
                        stage("Collect execution results") {
                            steps {
                                script {
                                    karateTestsExecutionSummary = karateTestUtils.collectTestsResults("**/target/karate-reports*/karate-summary-json.txt")
                                    karateTestUtils.attachCucumberReports(karateTestsExecutionSummary)
                                }
                            }
                        }

                        stage("Parse teams assignment") {
                            steps {
                                script {
                                    def jsonContents = readJSON file: "teams-assignment.json"
                                    teamAssignment = new TeamAssignment(jsonContents)
                                }
                            }
                        }

                        stage("Sync jira tickets") {
                            steps {
                                script {
                                    karateTestUtils.syncJiraIssues(karateTestsExecutionSummary, teamAssignment)
                                }
                            }
                        }

                        stage("Send slack notifications") {
                            steps {
                                script {
                                    karateTestUtils.sendSlackNotification(karateTestsExecutionSummary, teamAssignment)
                                }
                            }
                        }
                    }
                }
            }
        }

        stage("Set job execution result") {
            when {
                expression {
                    spinUpEnvironmentJob.result != 'SUCCESS'
                }
            }
            steps {
                script {
                    currentBuild.result = 'FAILURE'
                }
            }
        }
    }
    }

    catch (Exception ex) {
        println("Error message:" + ex)
    }
    finally {
        stage("Cleanup environment") {
            steps {
                script {
                    def jobParameters = getDestroyEnvironmentJobParameters(clusterName, projectName)

                    tearDownEnvironmentJob = build job: destroyEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }
    }
}
private List getEnvironmentJobParameters(String action, String okapiVersion, clusterName, projectName, tenant,
                                         folio_repository, folio_branch) {
    [
        string(name: 'CLUSTER', value: clusterName),
        string(name: 'NAMESPACE', value: projectName),
        string(name: 'FOLIO_BRANCH', value: folio_branch),
        string(name: 'OKAPI_VERSION', value: okapiVersion),
        string(name: 'CONFIG_TYPE', value: "testing"),
        booleanParam(name: 'LOAD_REFERENCE', value: true),
        booleanParam(name: 'LOAD_SAMPLE', value: true),
        booleanParam(name: 'CONSORTIA', value: true),
        booleanParam(name: 'RW_SPLIT', value: true),
        booleanParam(name: 'GREENMAIL', value: false),
        string(name: 'POSTGRESQL', value: 'built-in'),
        string(name: 'KAFKA', value: 'built-in'),
        string(name: 'OPENSEARCH', value: 'built-in'),
        string(name: 'S3_BUCKET', value: 'built-in'),
        string(name: 'MEMBERS', value: ''),
        string(name: 'AGENT', value: 'rancher'),
        booleanParam(name: 'REFRESH_PARAMETERS', value: false)
    ]
}
private List getDestroyEnvironmentJobParameters(clusterName, projectName) {
    [
        string(name: 'CLUSTER', value: clusterName),
        string(name: 'NAMESPACE', value: projectName),
        booleanParam(name: 'RW_SPLIT', value: true),
        string(name: 'POSTGRESQL', value: 'built-in'),
        string(name: 'KAFKA', value: 'built-in'),
        string(name: 'OPENSEARCH', value: 'built-in'),
        string(name: 'S3_BUCKET', value: 'built-in'),
        string(name: 'AGENT', value: 'rancher'),
        booleanParam(name: 'REFRESH_PARAMETERS', value: false)]
}
