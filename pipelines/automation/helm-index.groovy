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
                withCredentials([
                    usernamePassword(credentialsId: Constants.NEXUS_PUBLISH_CREDENTIALS_ID, usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD'),
                ]) {
                    helm.k8sClient {
                        sh """
                            echo "DEBUG 0"
                            AUTH="$NEXUS_USERNAME:$NEXUS_PASSWORD"
                            echo "DEBUG 1"
                            CHART_PACKAGE="\$(helm package edge-caiasoft/ --dependency-update | cut -d":" -f2 | tr -d '[:space:]')"
                            echo "DEBUG 2"
                            echo \$CHART_PACKAGE
                            ls
                            echo "DEBUG 3"
                            echo "Pushing \$CHART_PACKAGE to repo Nexus ..."
                            curl -is -u "\$AUTH" https://repository.folio.org/repository/folio-helm-v2-test/ --upload-file "\$CHART_PACKAGE"

                        """
                    }
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

