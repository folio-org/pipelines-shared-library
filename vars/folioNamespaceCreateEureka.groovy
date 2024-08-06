import org.folio.Constants
import org.folio.models.*
import org.folio.models.parameters.CreateNamespaceParameters
import org.folio.rest.GitHubUtility
import org.folio.rest_v2.eureka.Eureka

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

void call(CreateNamespaceParameters args) {
  try {
    println("Create operation parameters:\n${prettyPrint(toJson(args))}")

    RancherNamespace namespace = new RancherNamespace(args.clusterName, args.namespaceName)
    //Set terraform configuration
    TerraformConfig tfConfig = new TerraformConfig('terraform/rancher/project')
      .withWorkspace("${args.clusterName}-${args.namespaceName}")

    tfConfig.addVar('rancher_cluster_name', args.clusterName)
    tfConfig.addVar('rancher_project_name', args.namespaceName)
    tfConfig.addVar('pg_password', Constants.PG_ROOT_DEFAULT_PASSWORD)
    tfConfig.addVar('pgadmin_password', Constants.PGADMIN_DEFAULT_PASSWORD)
    tfConfig.addVar('pg_embedded', args.pgType == 'built-in')
    tfConfig.addVar('kafka_shared', args.kafkaType != 'built-in')
    tfConfig.addVar('opensearch_shared', args.opensearchType != 'built-in')
    tfConfig.addVar('s3_embedded', args.s3Type == 'built-in')
    tfConfig.addVar('pgadmin4', 'true')
    tfConfig.addVar('enable_rw_split', args.rwSplit)
    tfConfig.addVar('pg_ldp_user_password', Constants.PG_LDP_DEFAULT_PASSWORD)
    tfConfig.addVar('github_team_ids', folioTools.getGitHubTeamsIds("${Constants.ENVS_MEMBERS_LIST[args.namespaceName]},${args.members}").collect { "\"${it}\"" })
    tfConfig.addVar('pg_version', args.pgVersion)
    tfConfig.addVar('eureka', args.eureka)

//    stage('[Terraform] Provision') {
//      folioTerraformFlow.manageNamespace('apply', tfConfig)
//    }

//    stage('[Wait] for keycloak initialization') {
//      sleep time: 3, unit: 'MINUTES' // keycloak init timeout | MUST HAVE
//    }

//    if (args.greenmail) {
//      stage('[Helm] Deploy greenmail') {
//        folioHelm.withKubeConfig(namespace.getClusterName()) {
//          folioHelmFlow.deployGreenmail(namespace.getNamespaceName())
//        }
//      }
//    }

//    if (args.mockServer) {
//      stage('[Helm] Deploy mock-server') {
//        folioHelm.withKubeConfig(namespace.getClusterName()) {
//          folioHelmFlow.deployMockServer(namespace)
//        }
//      }
//    }

    if (args.namespaceOnly) {
      return
    }

    //Set install configuration
    String defaultTenantId = 'diku'
    String folioRepository = 'application-descriptors'
    boolean releaseVersion = true
    String commitHash = common.getLastCommitHash('platform-complete', 'snapshot')
    List installJson = new GitHubUtility(this).getEnableList(folioRepository, 'master/Quesnelia')
    def eurekaPlatform = new GitHubUtility(this).getEurekaList('platform-complete', 'snapshot')
    TenantUi tenantUi = new TenantUi("${namespace.getClusterName()}-${namespace.getNamespaceName()}",
      commitHash, 'snapshot')
    InstallRequestParams installRequestParams = new InstallRequestParams()
      .withTenantParameters("loadReference=${args.loadReference},loadSample=${args.loadSample}")

    namespace.withSuperTenantAdminUser().withOkapiVersion(args.okapiVersion).withDefaultTenant(defaultTenantId)
      .withDeploymentConfigType(args.configType)
    namespace.setEnableEureka(args.eureka)
    namespace.setEnableRtr(args.rtr)
    namespace.addDeploymentConfig(folioTools.getPipelineBranch())
    installJson.addAll(eurekaPlatform)
    namespace.getModules().setInstallJson(installJson)

    namespace.addTenant(folioDefault.tenants()[namespace.getDefaultTenantId()]
      .withInstallJson(namespace.getModules().getInstallJson().collect())
      .withIndex(new Index(true, true))
      .withInstallRequestParams(installRequestParams.clone())
      .withTenantUi(tenantUi.clone())
      .withTenantName("diku2")
    )

//    stage('[Helm] Deploy mgr-*') {
//      folioHelm.withKubeConfig(namespace.getClusterName()) {
//        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getMgrModules())
//      }
//    }

    stage('[Rest] MDs and SVC') {
      //tbd
    }

//    stage('[Helm] Deploy modules') {
//      folioHelm.withKubeConfig(namespace.getClusterName()) {
//        println(namespace.getModules().getBackendModules())
//        input("Paused for review...")
//        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getBackendModules())
//        if (namespace.getModules().getBackendModules()['mod-login']) {
//          sh(script: "helm uninstall mod-login --namespace=${namespace.getNamespaceName()}")
//        }
//        sh(script: "kubectl set env deployment/mod-consortia-keycloak MOD_USERS_ID=mod-users-${namespace.getModules().allModules['mod-users']} --namespace=${namespace.getNamespaceName()}")
//      }
//    }
//
//    stage('[Rest] Configure edge') {
//      namespace.getModules().removeModule('edge-inventory')
//      namespace.getModules().removeModule('edge-erm')
//      namespace.getModules().removeModule('edge-users')
//      folioEdge.renderEphemeralProperties(namespace)
////      edge.createEdgeUsers(namespace.getTenants()[namespace.getDefaultTenantId()]) TODO should be replaced with Eureka Edge Users.
//    }

//    stage('[Helm] Deploy edge') {
//      folioHelm.withKubeConfig(namespace.getClusterName()) {
//        namespace.getModules().getEdgeModules().each { name, version -> kubectl.createConfigMap("${name}-ephemeral-properties", namespace.getNamespaceName(), "./${name}-ephemeral-properties")
//        }
//        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getEdgeModules())
//      }
//    }

    Eureka eureka = new Eureka(this, namespace.generateDomain('kong'), namespace.generateDomain('keycloak'))

    stage('[Rest] Initialize') {
      retry(2) {
        eureka.initializeFromScratch(namespace.getTenants(), namespace.getEnableConsortia())
      }
    }

//    if (args.uiBuild) {
//      stage('Build and deploy UI') {
//        Map branches = [:]
//        namespace.getTenants().each { tenantId, tenant ->
//          if (tenant.getTenantUi()) {
//            TenantUi ui = tenant.getTenantUi()
//            branches[tenantId] = {
//              def jobParameters = [eureka        : args.eureka,
//                                   kongUrl       : "https://${namespace.getDomains()['kong']}",
//                                   keycloakUrl   : "https://${namespace.getDomains()['keycloak']}",
//                                   tenantUrl     : "https://${namespace.generateDomain(tenantId)}",
//                                   hasAllPerms   : true,
//                                   isSingleTenant: true,
//                                   tenantOptions : """{${tenantId}: {name: "${tenantId}", clientId: "${tenantId}-application"}}""",
//                                   tenantId      : ui.getTenantId(),
//                                   custom_hash   : ui.getHash(),
//                                   custom_url    : "https://${namespace.getDomains()['kong']}",
//                                   custom_tag    : ui.getTag(),
//                                   consortia     : tenant instanceof OkapiTenantConsortia,
//                                   clientId      : ui.getTenantId() + "-application",
//                                   rancher_cluster_name: namespace.getClusterName(),
//                                   rancher_project_name: namespace.getNamespaceName()]
//
//              uiBuild(jobParameters, releaseVersion)
//              folioHelm.withKubeConfig(namespace.getClusterName()) {
//                folioHelm.deployFolioModule(namespace, 'ui-bundle', ui.getTag(), false, ui.getTenantId())
//              }
//            }
//          }
//        }
//        parallel branches
//      }
//    }

//    stage('Deploy ldp') {
//      folioHelm.withKubeConfig(namespace.getClusterName()) {
//        folioHelmFlow.deployLdp(namespace)
//      }
//    }

  } catch (Exception e) {
    println(e)
//    slackNotifications.sendPipelineFailSlackNotification('#rancher_tests_notifications')
    throw new Exception(e)
  }
}

