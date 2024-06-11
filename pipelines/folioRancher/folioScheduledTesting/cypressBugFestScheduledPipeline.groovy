@Library('pipelines-shared-library') _

import org.jenkinsci.plugins.workflow.libs.Library

def uiUrl = "https://bugfest-orchid-aqa.int.aws.folio.org"
def okapiUrl = "https://okapi-bugfest-orchid-aqa.int.aws.folio.org"
def tenant = "fs09000003"

properties([
  disableConcurrentBuilds(),
  pipelineTriggers([cron('H 2 * * 1-6')]),
  parameters([
    string(name: 'branch', defaultValue: 'orchid-parallel', description: 'Cypress tests repository branch to checkout')
  ]),
])

def jobParameters = [
  branch           : params.branch,
  uiUrl            : uiUrl,
  okapiUrl         : okapiUrl,
  tenant           : tenant,
  user             : 'folio-aqa',
  password         : 'Folio-aqa1',
  cypressParameters: ["--group parallelTests --spec 'cypress/e2e/parallel/**/*'", "--group parallelTests --spec 'cypress/e2e/parallel/**/*'", "--group parallelTests --spec 'cypress/e2e/parallel/**/*'", "--group nonParallelTests --spec 'cypress/e2e/nonParallel/**/*'"],
  customBuildName  : JOB_BASE_NAME,
  timeout          : '6',
  testrailProjectID: '14',
  testrailRunID    : '2151',
  numberOfWorkers  : '4',
  agent            : 'rancher'
]

node {
  timeout(time: 5, unit: 'HOURS') {
    cypressFlow(jobParameters)
  }
}
