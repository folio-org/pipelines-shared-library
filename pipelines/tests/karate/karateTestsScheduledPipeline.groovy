package tests.karate

@Library('pipelines-shared-library') _

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
def prototypeTenant = "diku"

def spinUpEnvironmentJobName = "/Rancher/Project"
def spinUpEnvironmentJob
def tearDownEnvironmentJob

KarateTestsExecutionSummary karateTestsExecutionSummary
def teamAssignment

Tools tools = new Tools(this)
List<String> versions = tools.eval(jobsParameters.getOkapiVersions(), ["folio_repository": folio_repository, "folio_branch": folio_branch])
String okapiVersion = versions[0] //versions.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: [VersionConstants.MASTER_BRANCH]))[0]

pipeline {
    agent { label 'rancher' }

    triggers {
        cron('H 3 * * *')
    }

    options {
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
    }

    stages {
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
                            def jobParameters = getEnvironmentJobParameters('destroy', okapiVersion, clusterName,
                                projectName, prototypeTenant, folio_repository, folio_branch)

                            tearDownEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
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

private List getEnvironmentJobParameters(String action, String okapiVersion, clusterName, projectName, tenant,
                                         folio_repository, folio_branch) {
    [
        string(name: 'action', value: action),
        string(name: 'config_type', value: "testing"),
        string(name: 'rancher_cluster_name', value: clusterName),
        string(name: 'rancher_project_name', value: projectName),
        string(name: 'okapi_version', value: okapiVersion),
        booleanParam(name: 'ui_bundle_build', value: true),
        booleanParam(name: 'enable_modules', value: true),
        string(name: 'folio_repository', value: folio_repository),
        string(name: 'folio_branch', value: folio_branch),
        string(name: 'tenant_id', value: tenant),
        string(name: 'tenant_name', value: "Karate tenant"),
        string(name: 'tenant_description', value: "Karate tests main tenant"),
        booleanParam(name: 'load_reference', value: true),
        booleanParam(name: 'load_sample', value: true),
        booleanParam(name: 'pg_embedded', value: true),
        booleanParam(name: 'kafka_shared', value: true),
        booleanParam(name: 'opensearch_shared', value: false),
        booleanParam(name: 's3_embedded', value: true),
        booleanParam(name: 'greenmail_server', value: true)
    ]
}
