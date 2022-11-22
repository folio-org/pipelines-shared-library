#!groovy

@Library('pipelines-shared-library') _

import org.folio.Constants
import org.folio.utilities.Tools
import groovy.json.JsonSlurperClassic
import org.jenkinsci.plugins.workflow.libs.Library

def chartsRepositoryUrl = "${Constants.FOLIO_GITHUB_URL}/folio-helm-v2.git"

ansiColor('xterm') {
    node('jenkins-agent-java11') {
        try {
            stage('Checkout') {
                sshagent(credentials: [Constants.GITHUB_CREDENTIALS_ID]) {
                    checkout([
                        $class           : 'GitSCM',
                        branches         : [[name: "*/master"]],
                        extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
                                                            disableSubmodules  : false,
                                                            parentCredentials  : false,
                                                            recursiveSubmodules: true,
                                                            reference          : '',
                                                            trackingSubmodules : false]],
                        userRemoteConfigs: [[url: chartsRepositoryUrl]]
                    ])
                }
            }
            stage("Test") {
                helm.k8sClient {
                    sh "ls"
                    sh """
                        CHART_PACKAGE="\$(helm package edge-caiasoft/ --dependency-update | cut -d":" -f2 | tr -d '[:space:]')"
                        echo \$CHART_PACKAGE
                        ls
                    """
                    cleanWs()
                }
            }
        } catch (exception) {
            println(exception)
            error(exception.getMessage())
        } finally {
            stage('Cleanup') {
                cleanWs notFailBuild: true
            }
        }
    }
}




// import org.folio.Constants
// import org.jenkinsci.plugins.workflow.libs.Library
// import hudson.util.Secret

// @Library('pipelines-shared-library') _

// def chartsRepositoryUrl = "${Constants.FOLIO_GITHUB_URL}/folio-helm-v2.git"

// pipeline {
//     agent { label 'jenkins-agent-java11' }

//     stages {
//         stage('Checkout') {
//             steps {
//                 script {
//                     sshagent(credentials: [Constants.GITHUB_CREDENTIALS_ID]) {
//                         checkout([
//                             $class           : 'GitSCM',
//                             branches         : [[name: "*/master"]],
//                             extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
//                                                                   disableSubmodules  : false,
//                                                                   parentCredentials  : false,
//                                                                   recursiveSubmodules: true,
//                                                                   reference          : '',
//                                                                   trackingSubmodules : false]],
//                             userRemoteConfigs: [[url: chartsRepositoryUrl]]
//                         ])
//                     }
//                 }
//             }
//         }

//         stage('Build tests') {
//             steps {
//                 helm.k8sClient {
//                     sh "ls"
//                     sh """
//                         CHART_PACKAGE="\$(helm package edge-caiasoft/ --dependency-update | cut -d":" -f2 | tr -d '[:space:]')"
//                         echo \$CHART_PACKAGE
//                         ls
//                     """
//                     cleanWs()
//                 }
//             }
//         }
//     }
// }

