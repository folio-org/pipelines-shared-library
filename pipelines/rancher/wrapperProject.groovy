#!groovy
@Library('pipelines-shared-library') _

import org.folio.Constants
import org.folio.rest.Deployment
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiUser
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.Tools
import org.jenkinsci.plugins.workflow.libs.Library


properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        choice(name: 'action', choices: ['apply', 'destroy', 'nothing'], description: '(Required) Choose what should be done with cluster'),
        jobsParameters.repository(),
        jobsParameters.folioBranch(),
        jobsParameters.okapiVersion(),
//        jobsParameters.projectName(),
        choice(name: 'projectName', choices: ["bama","concorde","core-platform","ebsco-core","falcon","firebird","folijet",
                                              "metadata","prokopovych","scout","spanish","spitfire","sprint-testing",
                                              "stripes-force","thor","thunderjet","unam","vega","volaris","volaris-2nd"],
                                              description: "(Required) Select project to operate"),
        jobsParameters.loadReference(),
        jobsParameters.loadSample()
    ])
])
["bama","concorde","core-platform","ebsco-core","falcon","firebird","folijet","metadata","prokopovych","scout","spanish","spitfire","sprint-testing","stripes-force","thor","thunderjet","unam","vega","volaris","volaris-2nd"]
def rancherClusters = "folio-dev"
//def frontendImageTag = "folio-testing-karate-diku-e025d02"
def tenantId = "diku"
def tenantName = "Datalogisk Institut"
def tenantDescription = "Danish Library Technology Institute"
//def pgPassword = "postgres_password_123!"
//def pgAdminPassword    = "SuperSecret"
def envConfig = "development"
def agents = "jenkins-agent-java11"

//def testrefreshParameters = false
//def testaction = params.action
//def testloadReference = params.loadReference
//def testfolio_repository = params.repository
//def testfolio_branch = params.folioBranch
//def testokapi_version = params.okapiVersion
//def testrancher_cluster_name = rancherClusters
//def testrancher_project_name = params.projectName
//def testbuild_ui = true
//def testfrontendImageTag = frontendImageTag
//def testenvType = envConfig
//def testenableModules = true
//def testagents = agents
//def testtenantId = tenantId
//def testtenantName = tenantName
//def testtenantDescription = tenantDescription
//def testreindexElasticsearch = true
//def testrecreateindexElasticsearch = false
//def testload_reference = params.loadReference
//def testload_sample = params.loadSample
//def testpgPassword = pgPassword
//def testpgAdminPassword = pgAdminPassword
//def testgithub_teams = ''
//def testrestorePostgresqlFromBackup = false
//def testtenantIdToRestoreModulesVersions = tenantId
//def testrestorePostgresqlBackupName = ''
//def testpg_embedded = true
//def testkafka_embedded = true
//def testes_embedded = true
//def tests3_embedded = true
//def testpgadmin4 = true
node('jenkins-agent-java11') {
//    stage('Test') {
//        println(testaction)
//        println(testloadReference)
//        println(testfolio_repository)
//        println(testfolio_branch)
//        println(testokapi_version)
//        println(testrancher_cluster_name)
//        println(testrancher_project_name)
//        println(testbuild_ui)
//        println(testload_sample)
//        println(testload_reference)
//        println(testrefreshParameters)
//        println(testfrontendImageTag)
//        println(testenvType)
//        println(testenableModules)
//        println(testagents)
//        println(testtenantId)
//        println(testtenantName)
//        println(testtenantDescription)
//        println(testreindexElasticsearch)
//        println(testrecreateindexElasticsearch)
//        println(testpgPassword)
//        println(testpgAdminPassword)
//        println(testgithub_teams)
//        println(testrestorePostgresqlFromBackup)
//        println(testtenantIdToRestoreModulesVersions)
//        println(testrestorePostgresqlBackupName)
//        println(testpg_embedded)
//        println(testkafka_embedded)
//        println(testes_embedded)
//        println(tests3_embedded)
//        println(testpgadmin4)
//    }
//}
        try {
            stage('Build project Job') {
                if (params.action == 'apply') {
                    build job: 'Rancher/Project',
                        parameters: [
//                            booleanParam(name: 'refreshParameters', value: false),
                            string(name: 'action', value: params.action),
                            string(name: 'folio_repository', value: params.folio_repository),
                            string(name: 'folio_branch', value: params.folio_branch),
                            string(name: 'okapi_version', value: params.okapi_version),
                            string(name: 'rancher_cluster_name', value: rancherClusters),
                            string(name: 'rancher_project_name', value: params.projectName),
//                            booleanParam(name: 'build_ui', value: true),
//                            string(name: 'frontend_image_tag', value: frontendImageTag),
                            booleanParam(name: 'env_config', value: envConfig),
                            booleanParam(name: 'enable_modules', value: true),
//                            string(name: 'agent', value: agents),
//                            string(name: 'tenant_id', value: tenantId),
//                            string(name: 'tenant_name', value: tenantName),
//                            string(name: 'tenant_description', value: tenantDescription),
                            booleanParam(name: 'reindex_elastic_search', value: true),
                            booleanParam(name: 'recreate_index_elastic_search', value: false),
                            booleanParam(name: 'load_reference', value: params.load_reference),
                            booleanParam(name: 'load_sample', value: params.load_sample),
//                            string(name: 'pg_password', value: pgPassword),
//                            string(name: 'pgadmin_password', value: pgAdminPassword),
//                            string(name: 'github_teams', value: ''),
//                            booleanParam(name: 'restore_postgresql_from_backup', value: false),
//                            string(name: 'restore_tenant_id', value: tenantId),
//                            booleanParam(name: 'restore_postgresql_backup_name', value: ''),
                            booleanParam(name: 'pg_embedded', value: true),
                            booleanParam(name: 'kafka_embedded', value: true),
                            booleanParam(name: 'es_embedded', value: true),
                            booleanParam(name: 's3_embedded', value: true),
                            booleanParam(name: 'pgadmin4', value: true)
                        ]
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

