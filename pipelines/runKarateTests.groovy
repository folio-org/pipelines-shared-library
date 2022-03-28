import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-239') _

def karateEnvironment = "jenkins"

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'okapiUrl', defaultValue: 'https://ptf-perf-okapi.ci.folio.org', description: 'Target environment OKAPI URL')
        string(name: 'tenant', defaultValue: 'fs09000000', description: 'Tenant name for tests execution')
        string(name: 'adminUserName', defaultValue: 'folio', description: 'Admin user name')
        string(name: 'adminPassword', defaultValue: 'folio', description: 'Admin user password')
        //password(name: 'adminPassword', defaultValue: 'folio', description: 'Admin user password')
    }

    stages {
        stage("Checkout") {
            steps {
                script {
                    sshagent(credentials: ['11657186-f4d4-4099-ab72-2a32e023cced']) {
                        checkout([
                            $class           : 'GitSCM',
                            branches         : [[name: "*/${params.branch}"]],
                            extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
                                                                  disableSubmodules  : false,
                                                                  parentCredentials  : false,
                                                                  recursiveSubmodules: true,
                                                                  reference          : '',
                                                                  trackingSubmodules : false]],
                            userRemoteConfigs: [[url: 'https://github.com/folio-org/folio-integration-tests.git']]
                        ])
                    }
                }
            }
        }

        stage("Build karate config") {
            steps {
                script {
                    def jenkinsKarateConfigContents = getKarateConfig()
                    def files = findFiles(glob: '**/karate-config.js')

                    files.each { file ->
                        String path = file.path.replaceAll("\\\\", "/")
                        def folderPath = path.substring(0, path.lastIndexOf("/"))

                        echo "Creating file ${folderPath}/karate-config-${karateEnvironment}.js"
                        writeFile file: "${folderPath}/karate-config-${karateEnvironment}.js", text: jenkinsKarateConfigContents
                    }
                }
            }
        }

        stage('Run karate tests') {
            steps {
                script {
                    withMaven(
                        jdk: 'openjdk-11-jenkins-slave-all',
                        maven: 'maven3-jenkins-slave-all',
                        mavenSettingsConfig: 'folioci-maven-settings'
                    ) {
                        sh "mvn test -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment}"

//                        withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'testrail_password', usernameVariable: 'testrail_user'), string(credentialsId: 'mod-kb-ebsco-key', variable: 'ebsco_key'), string(credentialsId: 'mod-kb-ebsco-url', variable: 'ebsco_url'), string(credentialsId: 'mod-kb-ebsco-id', variable: 'ebsco_id'), string(credentialsId: 'mod-kb-ebsco-usageId', variable: 'ebsco_usage_id'), string(credentialsId: 'mod-kb-ebsco-usageSecret', variable: 'ebsco_usage_secret'), string(credentialsId: 'mod-kb-ebsco-usageKey', variable: 'ebsco_usage_key')]) {
//                            sh """
//      export kbEbscoCredentialsApiKey=${ebsco_key}
//      export kbEbscoCredentialsUrl=${ebsco_url}
//      export kbEbscoCredentialsCustomerId=${ebsco_id}
//      export usageConsolidationCredentialsId=${ebsco_usage_id}
//      export usageConsolidationCredentialsSecret=${ebsco_usage_secret}
//      export usageConsolidationCustomerKey=${ebsco_usage_key}
//      mvn test -Dkarate.env=${okapiDns} -DfailIfNoTests=false -Dtestrail_url=${TestRailUrl} -Dtestrail_userId=${testrail_user} -Dtestrail_pwd=${testrail_password} -Dtestrail_projectId=${TestRailProjectId} -DkbEbscoCredentialsApiKey=${ebsco_key} -DkbEbscoCredentialsUrl=${ebsco_url} -DkbEbscoCredentialsCustomerId=${ebsco_id} -DusageConsolidationCredentialsId=${ebsco_usage_id} -DusageConsolidationCredentialsSecret=${ebsco_usage_secret} -DusageConsolidationCustomerKey=${ebsco_usage_key}
//      """
                    }
                }
            }
        }

        stage('Publish tests report') {
            steps {
                script {
                    cucumber buildStatus: "UNSTABLE",
                        fileIncludePattern: "**/target/karate-reports/*.json"
                }
            }
        }
    }
}

def getKarateConfig() {
    """
function fn() {
    var config = {
        baseUrl: '${params.okapiUrl}',
        admin: {
            tenant: '${params.tenant}',
            name: '${params.adminUserName}',
            password: '${adminPassword}'
        }
    }

    return config;
}
"""
}




