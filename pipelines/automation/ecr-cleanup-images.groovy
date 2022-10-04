@Library('pipelines-shared-library@RANCHER-466') _

import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library

pipeline {
    agent { label 'jenkins-agent-java11' }

    triggers {
        cron('H 0 * * 1-6')
    }

    options {
        disableConcurrentBuilds()
    }

    /*parameters {
        string(name: 'branch', defaultValue: 'master', description: '')
    }*/

    stages {
        stage("Cleanup us-west-2 ui-bundle repo") {
            steps {
                script {
                    jobsParameters.clustersList().each {val->
                        println(val)
                    }
                    def test = jobsParameters.getBackendModulesList()
                    println(test)
                }
            }
        }

        stage("Cleanup us-west-2 mod-* and okapi repos") {
            steps {
                script {
                    println('test')
                }
            }
        }
    }
}
