import org.folio.Constants
import org.folio.models.*
import org.folio.models.parameters.CreateNamespaceParameters
import org.folio.rest.GitHubUtility

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

    stage('[Terraform] Provision') {
      folioTerraformFlow.manageNamespace('apply', tfConfig)
    }

    stage('[Wait] for keycloak initialization') {
      sleep time: 3, unit: 'MINUTES' // keycloak init timeout | MUST HAVE
    }

    if (args.greenmail) {
      stage('[Helm] Deploy greenmail') {
        folioHelm.withKubeConfig(namespace.getClusterName()) {
          folioHelmFlow.deployGreenmail(namespace.getNamespaceName())
        }
      }
    }

    if (args.mockServer) {
      stage('[Helm] Deploy mock-server') {
        folioHelm.withKubeConfig(namespace.getClusterName()) {
          folioHelmFlow.deployMockServer(namespace)
        }
      }
    }

    if (args.namespaceOnly) {
      return
    }

    //Set install configuration
    String defaultTenantId = 'diku'
    String folioRepository = 'platform-complete'
    boolean releaseVersion = args.folioBranch ==~ /^R\d-\d{4}.*/
    String commitHash = common.getLastCommitHash(folioRepository, args.folioBranch)
    List installJson = new GitHubUtility(this).getEnableList(folioRepository, args.folioBranch)
    List eurekaPlatform = new GitHubUtility(this).getEurekaList(folioRepository, args.folioBranch)
    TenantUi tenantUi = new TenantUi("${namespace.getClusterName()}-${namespace.getNamespaceName()}",
      commitHash, args.folioBranch)
    InstallRequestParams installRequestParams = new InstallRequestParams()
      .withTenantParameters("loadReference=${args.loadReference},loadSample=${args.loadSample}")

    namespace.withSuperTenantAdminUser().withOkapiVersion(args.okapiVersion).withDefaultTenant(defaultTenantId)
      .withDeploymentConfigType(args.configType)
    namespace.setEnableEureka(args.eureka)
    namespace.setEnableRwSplit(args.rwSplit)
    namespace.setEnableRtr(args.rtr)
    namespace.addDeploymentConfig(folioTools.getPipelineBranch())
    installJson.addAll(eurekaPlatform)
    namespace.getModules().setInstallJson(installJson)

    namespace.addTenant(folioDefault.tenants()[namespace.getDefaultTenantId()]
      .withInstallJson(namespace.getModules().getInstallJson().collect())
      .withIndex(new Index(true, true))
      .withInstallRequestParams(installRequestParams.clone())
      .withTenantUi(tenantUi.clone())
    )
    if (args.consortia) {
      namespace.setEnableConsortia(true, releaseVersion)
      folioDefault.consortiaTenants(namespace.getModules().getInstallJson(), installRequestParams).values().each { tenant ->
        if (tenant.getIsCentralConsortiaTenant()) {
          tenant.withTenantUi(tenantUi.clone())
        }
        namespace.addTenant(tenant)
      }
    }

    stage('[Helm] Deploy mgr-*') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getMgrModules(), true)
      }
    }

    stage('[Rest] MDs and SVC') {
      //tbd
    }

    stage('[Helm] Deploy modules') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getBackendModules())
        sh(script: "helm uninstall mod-login --namespace=${namespace.getNamespaceName()}") // Workaround for mog-login-keycloak
      }
    }

    stage('[Rest] Configure edge') {
      folioEdge.renderEphemeralProperties(namespace)
//      edge.createEdgeUsers(namespace.getTenants()[namespace.getDefaultTenantId()]) TODO should be replaced with Eureka Edge Users.
    }

    stage('[Helm] Deploy edge') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        namespace.getModules().getEdgeModules().each { name, version ->
          kubectl.createConfigMap("${name}-ephemeral-properties", namespace.getNamespaceName(), "./${name}-ephemeral-properties")
        }
        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getEdgeModules())
      }
    }

    stage('[Build and deploy UI]') {
      // placeholder
    }

    stage('Deploy ldp') {
      println('LDP deployment')
    }
  } catch (Exception e) {
    println(e)
//    slackNotifications.sendPipelineFailSlackNotification('#rancher_tests_notifications')
    throw new Exception(e)
  }
}
