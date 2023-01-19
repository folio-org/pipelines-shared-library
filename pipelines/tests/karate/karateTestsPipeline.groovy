package tests.karate

import groovy.text.SimpleTemplateEngine
import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-332-edge') _

def karateEnvironment = "folio-testing-karate"
String clusterName = params.okapiUrl.minus("https://").split("-")[0,1].join("-")
String projectName = params.okapiUrl.minus("https://").split("-")[2]

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'modules', defaultValue: '', description: 'Comma separated modules list to build(no spaces). Leave empty to launch all.')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
        string(name: 'okapiUrl', defaultValue: 'https://folio-testing-karate-okapi.ci.folio.org', description: 'Target environment OKAPI URL')
        string(name: 'edgeUrl', defaultValue: 'https://folio-testing-karate-edge.ci.folio.org', description: 'Target environment EDGE URL')
        string(name: 'tenant', defaultValue: 'supertenant', description: 'Tenant name for tests execution')
        string(name: 'adminUserName', defaultValue: 'super_admin', description: 'Admin user name')
        password(name: 'adminPassword', defaultValue: 'admin', description: 'Admin user password')
        string(name: 'prototypeTenant', defaultValue: 'diku', description: 'A tenant name which will be used by tests as a prototype during test tenant creation')
        booleanParam(name: 'createCustomTenant', defaultValue: false, description: 'Do you need to create tenant for edge modules from edge-configuration.json file?')
    }

    stages {
        stage("Checkout") {
            steps {
                script {
                    sshagent(credentials: [Constants.GITHUB_CREDENTIALS_ID]) {
                        checkout([
                            $class           : 'GitSCM',
                            branches         : [[name: "*/${params.branch}"]],
                            extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
                                                                  disableSubmodules  : false,
                                                                  parentCredentials  : false,
                                                                  recursiveSubmodules: true,
                                                                  reference          : '',
                                                                  trackingSubmodules : false]],
                            userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/folio-integration-tests.git"]]
                        ])
                    }
                }
            }
        }
        stage("Prepare edge modules configuration") {
            when {
                expression {
                    params.createCustomTenant == true
                }
            }
            steps {
                script {
                    def jsonContents = readJSON file: "edge-configuration.json"
                    jsonContents.each {
                        String edgeName = it.name 
                        String configMapName = "${edgeName}-ephemeral-properties"

                        it.tenants.each {
                            def jobParameters = [
                                string(name: 'rancher_cluster_name', value: clusterName),
                                string(name: 'rancher_project_name', value: projectName),
                                string(name: 'edge_module', value: edgeName),
                                string(name: 'tenant_id', value: it.name),
                                string(name: 'tenant_name', value: it.name),
                                string(name: 'admin_username', value: it.user),
                                password(name: 'admin_password', value: it.password),
                                booleanParam(name: 'create_tenant', value: false)
                            ]
                            def ephemeralPropBuildJobResult = build job: "Rancher/Update/update-ephemeral-properties", parameters: jobParameters, wait: true, propagate: false
                            if (!fileExists(configMapName)) {
                                writeFile file: configMapName, text: ephemeralPropBuildJobResult.getBuildVariables()["existingConfigMap"]
                            }
                        }
                    }
                }
            }
        }

        // stage("Build karate config") {
        //     steps {
        //         script {
        //             def files = findFiles(glob: '**/karate-config.js')
        //                 files.each { file ->
        //                     echo "Updating file ${file.path}"
        //                     writeFile file: file.path, text: renderKarateConfig(readFile(file.path))
        //                 }

        //         }
        //     }
        // }

        // stage('Run karate tests') {
        //     steps {
        //         script {
        //             withMaven(
        //                 jdk: 'openjdk-11-jenkins-slave-all',
        //                 maven: 'maven3-jenkins-slave-all',
        //                 mavenSettingsConfig: 'folioci-maven-settings'
        //             ) {
        //                 def modules = ""
        //                 if (params.modules) {
        //                     modules = "-pl common,testrail-integration," + params.modules
        //                 }
        //                 sh "mvn test -T ${threadsCount} ${modules} -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment}"
        //             }
        //         }
        //     }
        // }

        // stage('Publish tests report') {
        //     steps {
        //         script {
        //             cucumber buildStatus: "UNSTABLE",
        //                 fileIncludePattern: "**/target/karate-reports*/*.json",
        //                 sortingMethod: "ALPHABETICAL"

        //             junit testResults: '**/target/karate-reports*/*.xml'
        //         }
        //     }
        // }

        // stage('Archive artifacts') {
        //     steps {
        //         script {
        //             // archive artifacts for upstream job
        //             if (currentBuild.getBuildCauses('org.jenkinsci.plugins.workflow.support.steps.build.BuildUpstreamCause')) {
        //                 zip zipFile: "cucumber.zip", glob: "**/target/karate-reports*/*.json"
        //                 zip zipFile: "junit.zip", glob: "**/target/karate-reports*/*.xml"
        //                 zip zipFile: "karate-summary.zip", glob: "**/target/karate-reports*/karate-summary-json.txt"

        //                 archiveArtifacts allowEmptyArchive: true, artifacts: "cucumber.zip", fingerprint: true, defaultExcludes: false
        //                 archiveArtifacts allowEmptyArchive: true, artifacts: "junit.zip", fingerprint: true, defaultExcludes: false
        //                 archiveArtifacts allowEmptyArchive: true, artifacts: "karate-summary.zip", fingerprint: true, defaultExcludes: false
        //                 archiveArtifacts allowEmptyArchive: true, artifacts: "teams-assignment.json", fingerprint: true, defaultExcludes: false
        //             }
        //         }
        //     }
        // }
    }
    post {
        always {
            script {
                helm.k8sClient {
                    awscli.getKubeConfig(Constants.AWS_REGION, clusterName)
                    def files = findFiles(glob: "**/*-ephemeral-properties")
                        files.each { file ->
                            kubectl.recreateConfigMap(file, projectName, "./${file}")  
                    }
                }
            }
        }
    }
}

String renderKarateConfig(String config){
    withCredentials([
        string(credentialsId: 'mod-kb-ebsco-url', variable: 'ebsco_url'),
        string(credentialsId: 'mod-kb-ebsco-id', variable: 'ebsco_id'),
        string(credentialsId: 'mod-kb-ebsco-key', variable: 'ebsco_key'),
        string(credentialsId: 'mod-kb-ebsco-usageId', variable: 'ebsco_usage_id'),
        string(credentialsId: 'mod-kb-ebsco-usageSecret', variable: 'ebsco_usage_secret'),
        string(credentialsId: 'mod-kb-ebsco-usageKey', variable: 'ebsco_usage_key')
    ]) {
        def engine = new SimpleTemplateEngine()
        Map binding = [
            "baseUrl"                            : params.okapiUrl,
            "edgeUrl"                            : params.edgeUrl,
            "admin"                              : [
                tenant  : params.tenant,
                name    : params.adminUserName,
                password: params.adminPassword
            ],
            "prototypeTenant"                    : params.prototypeTenant,
            "kbEbscoCredentialsUrl"              : ebsco_url,
            "kbEbscoCredentialsCustomerId"       : ebsco_id,
            "kbEbscoCredentialsApiKey"           : ebsco_key,

            "usageConsolidationCredentialsId"    : ebsco_usage_id,
            "usageConsolidationCredentialsSecret": ebsco_usage_secret,
            "usageConsolidationCustomerKey"      : ebsco_usage_key
        ]
        return engine.createTemplate(config.replaceAll(/(\\)/, /\\$0/)).make(binding).toString()
    }
}
