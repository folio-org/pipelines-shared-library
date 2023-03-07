@Library('pipelines-shared-library') _

import org.jenkinsci.plugins.workflow.libs.Library

def uiUrl = "https://bugfest-orchid-aqa.int.aws.folio.org"
def okapiUrl = "https://okapi-bugfest-orchid-aqa.int.aws.folio.org"
def tenant = "fs09000003"

properties([
    disableConcurrentBuilds(),
    pipelineTriggers([cron('H 2 * * 1-6')]),
    parameters([
        string(name: 'branch', defaultValue: 'orchid', description: 'Cypress tests repository branch to checkout')
    ]),
])

def jobParameters = [
    branch: params.branch,
    uiUrl: uiUrl,
    okapiUrl: okapiUrl,
    tenant: tenant,
    user: 'folio-aqa',
    password: 'Folio-aqa1',
    cypressParameters: "--env grepTags=\"smoke criticalPth\",grepFilterSpecs=true",
    customBuildName: JOB_BASE_NAME,
    timeout: '6',
    testrailProjectID: '14',
    testrailRunID: '2151',
    numberOfWorkers: '4',
    agent: 'rancher||jenkins-agent-java11'
]

node {
    //cypressFlow(jobParameters)
}
