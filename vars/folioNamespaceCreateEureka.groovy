import org.folio.Constants
import org.folio.models.*
import org.folio.models.parameters.CreateNamespaceParameters
import org.folio.rest.GitHubUtility
import org.folio.rest_v2.PlatformType
import org.folio.rest_v2.eureka.Eureka
import org.folio.rest_v2.eureka.kong.Applications
import org.folio.rest_v2.eureka.kong.Edge
import org.folio.utilities.Logger

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

void call(CreateNamespaceParameters args) {
  try {
    Logger logger = new Logger(this, 'folioNamespaceCreateEureka')

    logger.info("Create operation parameters:\n${prettyPrint(toJson(args))}")

    EurekaNamespace namespace = new EurekaNamespace(args.clusterName, args.namespaceName)
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
    tfConfig.addVar('eureka', args.platform == PlatformType.EUREKA)
    tfConfig.addVar('kong_version', args.kongVersion)
    tfConfig.addVar('keycloak_version', args.keycloakVersion)
    if (args.dataset) {
      tfConfig.addVar('pg_rds_snapshot_name', Constants.BUGFEST_SNAPSHOT_NAME)
      tfConfig.addVar('pg_dbname', Constants.BUGFEST_SNAPSHOT_DBNAME)
      tfConfig.addVar('pg_instance_type', 'db.r6g.xlarge')
    }

    boolean TF_REFRESH_REQUESTED = false

    stage('[Terraform] Provision') {
      switch (args.type) {
        case 'full':
          folioTerraformFlow.manageNamespace('apply', tfConfig)
          break
        case 'terraform':
          folioTerraformFlow.manageNamespace('apply', tfConfig)
          TF_REFRESH_REQUESTED = true
          break
        case 'update':
          logger.info("Skip [Terraform] Provision stage")
          break
      }
    }

    if (TF_REFRESH_REQUESTED) {
      currentBuild.result = 'ABORTED'
      currentBuild.description = 'Terraform refresh complete'
      return
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

    String defaultTenantId
    List installJson
    List eurekaPlatform

    //Set install configuration
    if (args.dataset) {
      defaultTenantId = 'fs09000000'
    } else {
      defaultTenantId = 'diku'
    }
    String folioRepository = 'platform-complete'
    boolean isRelease = args.folioBranch ==~ /^R\d-\d{4}.*/
    String commitHash = common.getLastCommitHash(folioRepository, args.folioBranch)
    if (args.dataset) {
      installJson = new GitHubUtility(this).getEnableList(folioRepository, 'snapshot')
      eurekaPlatform = new GitHubUtility(this).getEurekaList(folioRepository, 'snapshot')
      installJson.addAll(eurekaPlatform)
    } else {
      installJson = new GitHubUtility(this).getEnableList(folioRepository, args.folioBranch)
      eurekaPlatform = new GitHubUtility(this).getEnableList(folioRepository, args.folioBranch)
      installJson.addAll(eurekaPlatform)
    }

    //TODO: Temporary solution. Unused by Eureka modules have been removed.
    installJson.removeAll { module -> module.id =~ /(mod-login|mod-authtoken|mod-login-saml)-\d+\..*/ }
    installJson.removeAll { module -> module.id == 'okapi' }

    TenantUi tenantUi = new TenantUi("${namespace.getClusterName()}-${namespace.getNamespaceName()}",
      commitHash, args.folioBranch)

    EurekaRequestParams installRequestParams = new EurekaRequestParams()
      .withIgnoreErrors(true)
      .doLoadReference(args.loadReference)
      .doLoadSample(args.loadSample) as EurekaRequestParams

    namespace.withSuperTenantAdminUser()
      .withOkapiVersion(args.okapiVersion)
      .withDefaultTenant(defaultTenantId)
      .withDeploymentConfigType(args.configType)

    namespace.setEnableSplitFiles(args.splitFiles)
    namespace.setEnableRwSplit(args.rwSplit)
    namespace.setEnableRtr(args.rtr)
    namespace.setEnableECS_CCL(args.ecsCCL)
    namespace.addDeploymentConfig(folioTools.getPipelineBranch())

    namespace.addTenant(
      folioDefault.tenants()[namespace.getDefaultTenantId()]
        .convertTo(EurekaTenant.class)
        .withAWSSecretStoragePathName("${namespace.getClusterName()}-${namespace.getNamespaceName()}")
        .withInstallJson(installJson)
        .withIndex(new Index('instance', true, false))
        .withIndex(new Index('authority', true, false))
        .withIndex(new Index('location', true, false))
        .withInstallRequestParams(installRequestParams.clone())
        .withTenantUi(tenantUi.clone())
        .enableFolioExtensions(this, args.folioExtensions - 'consortia-eureka' - 'consortia')
    )

    if (args.dataset) {
      List nonECS = ['fs09000002', 'fs09000003']
      nonECS.each { tenantId ->
        namespace.addTenant(
          folioDefault.tenants()[tenantId]
            .convertTo(EurekaTenant.class)
            .withAWSSecretStoragePathName("${namespace.getClusterName()}-${namespace.getNamespaceName()}")
            .withInstallJson(installJson)
            .withIndex(new Index('instance', true, false))
            .withIndex(new Index('authority', true, false))
            .withIndex(new Index('location', true, false))
            .withInstallRequestParams(installRequestParams.clone())
            .withTenantUi(tenantUi.clone())
            .enableFolioExtensions(this, args.folioExtensions - 'consortia-eureka' - 'consortia')
        )
      }
    }

    if (args.folioExtensions.contains('consortia-eureka')) {
      namespace.setEnableConsortia(true, isRelease)

      DTO.convertMapTo(args.dataset ? folioDefault.tenants([], installRequestParams).findAll { it.value.getTenantId().startsWith('cs00000int') } :
        folioDefault.consortiaTenants([], installRequestParams), EurekaTenantConsortia.class).values().each { tenant ->
        tenant.withInstallJson(installJson)
          .withAWSSecretStoragePathName("${namespace.getClusterName()}-${namespace.getNamespaceName()}")

        if (tenant.getIsCentralConsortiaTenant()) {
          tenant.withTenantUi(tenantUi.clone())
//          tenant.okapiConfig.setLdpConfig(ldpConfig)
        }

        tenant.enableFolioExtensions(this, args.folioExtensions)
        namespace.addTenant(tenant)
      }
    }

    // TODO: Move this part to one of Eureka classes later. | DO NOT REMOVE | FIX FOR DNS PROPAGATION ISSUE!!!
    timeout(time: 25, unit: 'MINUTES') {
      def check = ''

      while (check == '') {
        try {
          check = sh(script: "curl --fail --silent https://${namespace.generateDomain('keycloak')}/admin/master/console/", returnStdout: true).trim()
          return check
        } catch (ignored) {
          logger.debug("DNS record: ${namespace.generateDomain('keycloak')} still not propagated!")
          sleep time: 5, unit: "SECONDS"
        }
      }
    }

    //Don't move from here because it increases Keycloak TTL before mgr modules to be deployed
    Eureka eureka = new Eureka(this, namespace.generateDomain('kong'), namespace.generateDomain('keycloak'))
      .defineKeycloakTTL()

    stage('[Helm] Deploy mgr-*') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getMgrModules())

        //Check availability of the mgr-applications /applications endpoint to ensure the module up and running
        int counter = 0
        retry(10) {
          sleep time: (counter == 0 ? 0 : 30), unit: 'SECONDS'
          counter++

          Applications.get(eureka.kong).getRegisteredApplications()
        }
      }
    }

    stage('[Rest] Preinstall') {
      namespace.withApplications(
        eureka.registerApplicationsFlow(
          //TODO: Refactoring is needed!!! Utilization of extension should be applied.
          // Remove this shit with consortia and linkedData. Apps have to be taken as it is.
          args.applications -
            (args.consortia ? [:] : ["app-consortia": "snapshot", "app-consortia-manager": "snapshot"]) -
            (args.consortia ? [:] : ["app-consortia": "master", "app-consortia-manager": "master"]) -
            (args.linkedData ? [:] : ["app-linked-data": "snapshot"]) -
            (args.linkedData ? [:] : ["app-linked-data": "master"])
          , namespace.getModules().getModuleVersionMap()
          , namespace.getTenants().values() as List<EurekaTenant>
        )
      )

      eureka.registerModulesFlow(
        namespace.getModules()
        , namespace.getApplications()
      )
    }

    stage('[Helm] Deploy modules') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        logger.info(namespace.getModules().getBackendModules())

        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getBackendModules())
        folioHelm.checkDeploymentsRunning(namespace.getNamespaceName(), namespace.getModules().getBackendModules())
      }
    }

    stage('[Helm] Deploy edge') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        folioEdge.renderEphemeralPropertiesEureka(namespace)
        namespace.getModules().getEdgeModules().each { module ->
          kubectl.createConfigMap("${module.name}-ephemeral-properties", namespace.getNamespaceName(), "./${module.name}-ephemeral-properties")
        }

        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getEdgeModules())
        folioHelm.checkDeploymentsRunning(namespace.getNamespaceName(), namespace.getModules().getEdgeModules())

      }
    }

    stage('[Rest] Initialize') {
      int counter = 0
      retry(10) {
        // The first wait time should be at least 10 minutes due to module's long time instantiation
        sleep time: (counter == 0 ? 10 : 2), unit: 'MINUTES'
        counter++

        eureka.initializeFromScratch(
          namespace.getTenants()
          , namespace.getClusterName()
          , namespace.getNamespaceName()
          , namespace.getEnableConsortia()
//        , args.dataset
        )
      }
    }

    stage('[Rest] Configure edge') {
      new Edge(this, "${namespace.generateDomain('kong')}", "${namespace.generateDomain('keycloak')}").createEurekaUsers(namespace)
    }

    if (args.uiBuild) {
      stage('Build and deploy UI') {
        Map branches = [:]
        namespace.getTenants().each { tenantId, tenant ->
          if (tenant.getTenantUi()) {
            branches[tenantId] = {
              folioUI.buildAndDeploy(namespace, tenant, args.platform == PlatformType.EUREKA, namespace.getDomains()['kong'] as String
                , namespace.getDomains()['keycloak'] as String, args.ecsCCL)
            }
          }
        }
        parallel branches
      }
    }

    //TODO: Add adequate slack notification https://folio-org.atlassian.net/browse/RANCHER-1892
    stage('[Notify] Eureka') {
      if (args.dataset) {
        logger.warning("SUCCESS: Eureka sprint testing env successfully built!!!")
      } else {
        slackSend(color: 'good', message: args.clusterName + "-" + args.namespaceName + " env successfully built\n" +
                "1. https://${namespace.generateDomain('diku')}\n" +
                "2. https://${namespace.generateDomain('consortium')}",
                channel: '#rancher_tests_notifications')
      }
    }

//    stage('Deploy ldp') {
//      folioHelm.withKubeConfig(namespace.getClusterName()) {
//        folioHelmFlow.deployLdp(namespace)
//      }
//    }

  } catch (Exception e) {
    println(e)
    //TODO: Add adequate slack notification https://folio-org.atlassian.net/browse/RANCHER-1892
      if (args.dataset) {
          slackSend(color: 'danger', message: "eureka-snapshot env build failed...\n" + "${env.BUILD_URL}", channel: '#eureka-sprint-testing')
      } else {
          slackSend(color: 'danger', message: "eureka-snapshot env build failed...\n" + "${env.BUILD_URL}", channel: '#rancher_tests_notifications')
      }
    throw new Exception(e)
  }
}
