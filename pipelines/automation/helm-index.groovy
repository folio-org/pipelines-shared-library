import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library
import hudson.util.Secret

@Library('pipelines-shared-library') _

def chartsRepositoryUrl = "${Constants.FOLIO_GITHUB_URL}/folio-helm-v2.git"

pipeline {
    agent { label 'jenkins-agent-java11' }

    stages {
        stage('Checkout') {
            steps {
                script {
                    buildName customBuildName
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
            }
        }

        stage('Build tests') {
            steps {
                sh "ls"
            }
        }
    }
}
