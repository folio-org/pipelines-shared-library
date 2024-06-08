@Library('pipelines-shared-library') _

import org.jenkinsci.plugins.workflow.libs.Library

def clusterName = "folio-testing"
def projectName = "spring"
def tenant = "diku"
def uiUrl = "https://${clusterName}-${projectName}-${tenant}.ci.folio.org"
def okapiUrl = "https://${clusterName}-${projectName}-okapi.ci.folio.org"
def edgeUrl = "https://${clusterName}-${projectName}-edge.ci.folio.org"
def prototypeTenant = "diku"

pipeline {
  agent { label 'rancher' }

  triggers {
    cron('H 20 * * *')
  }

  options {
    disableConcurrentBuilds()
  }

  parameters {
    string(name: 'branch', defaultValue: 'master', description: 'Cypress tests repository branch to checkout')
    string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
    string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
  }

  stages {
    stage("Run cypress tests") {
      steps {
        script {
          def jobParameters = [
            branch           : params.branch,
            uiUrl            : uiUrl,
            okapiUrl         : okapiUrl,
            tenant           : tenant,
            user             : 'diku_admin',
            password         : 'admin',
            cypressParameters: "--env grepTags=\"smoke criticalPth\",grepFilterSpecs=true",
            customBuildName  : JOB_BASE_NAME,
            timeout          : '6'
          ]
          cypressFlow(jobParameters)
        }
      }
    }

    stage("Run karate tests") {
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
          karateFlow(jobParameters)
        }
      }
    }

  }
}
