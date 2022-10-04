@Library('pipelines-shared-library@RANCHER-466') _

import org.folio.Constants
import org.folio.utilities.Tools
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
//                    List toRemove = []
//                    jobsParameters.clustersList().each {cluster->
//                        jobsParameters.devEnvironmentsList().each {project->
//                            def temp = list.findAll { s -> s ==~ /${cluster}-${project}-.*/ }
//                            if (!temp.isEmpty()){
//                                toRemove.addAll(temp.take(temp.size() - 1))
//                            }
//                        }
//                    }
//                    println(toRemove)
                    def list = awscli.listEcrImages(Constans.AWS_REGION, ui_bundle_repo_name)
                    println(list)
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
