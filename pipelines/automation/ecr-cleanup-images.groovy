#!groovy
@Library('pipelines-shared-library@RANCHER-466') _

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

String ui_bundle_repo_name = 'ui-bundle'

pipeline {
    agent { label 'jenkins-agent-java11' }

    triggers {
        cron('H 0 * * 1-6')
    }

    options {
        disableConcurrentBuilds()
    }

    stages {
        stage("Cleanup us-west-2 ui-bundle repo") {
            steps {
                script {
                    String repo_images = ''
                    helm.k8sClient {
                        repo_images = awscli.listEcrImages('us-west-2', ui_bundle_repo_name)
                    }
                    List to_remove = []
                    jobsParameters.clustersList().each {cluster->
                        jobsParameters.devEnvironmentsList().each {project->
                            def temp = list.findAll { s -> s ==~ /${cluster}-${project}-.*/ }
                            if (!temp.isEmpty()){
                                to_remove.addAll(temp.take(temp.size() - 1))
                            }
                        }
                    }
                    println(to_remove)
                }
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
