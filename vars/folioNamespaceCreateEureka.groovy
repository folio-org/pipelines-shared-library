import org.folio.Constants
import org.folio.jenkins.PodTemplates
import org.folio.models.*
import org.folio.models.application.ApplicationList
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
  PodTemplates podTemplates = new PodTemplates(this, true)
  Logger logger = new Logger(this, 'folioNamespaceCreateEureka')

  podTemplates.rancherJavaAgent() {
    try {
      stage('Ini') {
        folioCommon.updateBuildName("#${args.clusterName}-${args.namespaceName}.${env.BUILD_ID}")
        folioCommon.updateBuildDescription("Branch: ${args.folioBranch}\nConfig: ${args.configType}")
      }

      stage('Checkout') {
        checkout(scmGit(
          branches: [[name: "*/${folioTools.getPipelineBranch()}"]],
          extensions: [cloneOption(depth: 10, noTags: true, reference: '', shallow: true)],
          userRemoteConfigs: [[credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                               url          : "${Constants.FOLIO_GITHUB_URL}/pipelines-shared-library.git"]]))
      }

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

      stage('[Approve REQUIRED]') {
        if (args.pgType == 'aws') {
          folioCommon.kitfoxApproval()
        }
      }

      stage('[Terraform] Provision') {
        switch (args.type) {
          case 'full':
            folioTerraformFlow.manageNamespace('apply', tfConfig)
            break
          case 'terraform':
            folioTerraformFlow.manageNamespace('apply', tfConfig)

            currentBuild.result = 'ABORTED'
            currentBuild.description = 'Terraform refresh complete'
            return

            break
          case 'update':
            logger.info("Skip [Terraform] Provision stage")
            break
        }
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

      def defaultTenantId = args.dataset ? 'fs09000000' : 'diku'
      String folioRepository = 'platform-complete'
      boolean isRelease = args.folioBranch ==~ /^R\d-\d{4}.*/
      String commitHash = common.getLastCommitHash(folioRepository, args.folioBranch)

      List installJson = new GitHubUtility(this).getEnableList(folioRepository, args.folioBranch)
      List eurekaPlatform = new GitHubUtility(this).getEurekaList(folioRepository, args.folioBranch)
      List pinnedEurekaModules = new GitHubUtility(this).getEurekaPinnedList(folioRepository, args.folioBranch)
      installJson.addAll(eurekaPlatform)

      if (args.scNative) {
        String tag = (awscli.listEcrImages(Constants.AWS_REGION, 'folio-module-sidecar')).replaceAll('"', '')
          logger.info("Previously built SC image is 'CUSTOM/NATIVE'. Using it for Eureka env.")
          installJson.removeAll { module -> module.id =~ /folio-module-sidecar-.*/ }
          installJson.add([id: "folio-module-sidecar-" + tag, action: 'enable'])
          writeJSON (file: 'used-install.json', json: installJson, pretty: 4)
          archiveArtifacts 'used-install.json' // Archive used modules version for review

      }

      //TODO: Temporary solution. Unused by Eureka modules have been removed.
      installJson.removeAll { module -> module.id =~ /(mod-login|mod-authtoken|mod-login-saml)-\d+\..*/ }
      installJson.removeAll { module -> module.id == 'okapi' }

      pinnedEurekaModules.each { pinned ->
        if (installJson.find { it.id =~ /${pinned.module}-.*/ }) {
          installJson.removeAll { module -> module.id =~ /${pinned.module}-.*/ }
          installJson.add([id: "${pinned.module}-${pinned.version}", action: 'enable'])
        }
      }

      TenantUi tenantUi = new TenantUi("${namespace.getClusterName()}-${namespace.getNamespaceName()}",
        commitHash, args.folioBranch)

      EurekaRequestParams installRequestParams = new EurekaRequestParams()
        .withIgnoreErrors(true)
        .doLoadReference(args.loadReference)
        .doLoadSample(args.loadSample) as EurekaRequestParams

      // Prepare separate params for each consortia group to avoid duplicate centralTenantId
      def consortiaParams1 = installRequestParams.clone()
      def consortiaParams2 = installRequestParams.clone()

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
          .withInstallJson(installJson)
          .withIndex(new Index('instance', true, true))
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
              .withInstallJson(installJson)
              .withIndex(new Index('instance', true, true))
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

        Map defaultConsortiaTenants = args.dataset ?
          folioDefault.tenants([], installRequestParams).findAll { it.value.getTenantId().startsWith('cs00000int') } :
          folioDefault.consortiaTenants([], consortiaParams1 as InstallRequestParams)

        if (args.consortiaExtra) {
          defaultConsortiaTenants.putAll(folioDefault.consortiaTenantsExtra([], consortiaParams2 as InstallRequestParams))
        }


        DTO.convertMapTo(defaultConsortiaTenants, EurekaTenantConsortia.class)
          .values()
          .each { tenant ->
            tenant.withInstallJson(installJson)
              .withSecureTenant(args.hasSecureTenant && args.secureTenantId == tenant.getTenantId())

            if (tenant.getIsCentralConsortiaTenant())
              tenant.withTenantUi(tenantUi.clone())

            tenant.enableFolioExtensions(this, args.folioExtensions)
            namespace.addTenant(tenant)
          }
      }

      //In case update environment the reindex is not needed
      if (args.type == 'update')
        namespace.getTenants().values().each { tenant -> tenant.indexes.clear() }

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
      Boolean check = false
      timeout(time: 15, unit: 'MINUTES') {
        while (!check) {
          try {
            eureka.defineKeycloakTTL()
            check = true
          } catch (Exception e) {
            logger.warning("Keycloak TTL increase failed: ${e.getMessage()}")
            sleep time: 5, unit: 'SECONDS'
            check = false
          }
        }
      }

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
          if (args.type == 'update') {
            List sql_cmd = ['DELETE FROM public.module', 'DELETE FROM public.entitlement',
                            'DELETE FROM public.entitlement_module', 'DELETE FROM public.application',
                            'DELETE FROM public.application_flow']
            String pod = sh(script: "kubectl get pod -l 'app.kubernetes.io/name=pgadmin4' -o=name -n ${namespace.getNamespaceName()}", returnStdout: true).trim()
            sql_cmd.each { sh(script: "kubectl exec ${pod} --namespace ${namespace.getNamespaceName()} -- /usr/local/pgsql-16/psql -c '${it}'", returnStdout: true) }
          }
        }
      }

      stage('[Rest] Preinstall') {
        ApplicationList apps = []
        container('java') {
          apps = eureka.generateApplications(
            //TODO: Refactoring is needed!!! Utilization of extension should be applied.
            // Remove this shit with consortia and linkedData. Apps have to be taken as it is.
            args.applications -
              (args.consortia ? [:] : ["app-consortia": "snapshot", "app-consortia-manager": "snapshot"]) -
              (args.consortia ? [:] : ["app-consortia": "master", "app-consortia-manager": "master"]) -
              (args.linkedData ? [:] : ["app-linked-data": "snapshot"]) -
              (args.linkedData ? [:] : ["app-linked-data": "master"])
            , namespace.getModules()
          )
        }

        int counter = 0
        retry(5) {
          sleep time: (counter == 0 ? 0 : 30), unit: 'SECONDS'
          counter++
          eureka.registerApplications(apps)

        namespace.getTenants().values().each { it.assignApplications(apps)}
        namespace.withApplications(apps)

        eureka.registerModulesFlow(namespace.applications.getInstallJson())
      }
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
        if (args.dataset) { // Prepare for large dataset reindex
          folioHelm.withKubeConfig(namespace.getClusterName()) {

            kubectl.setKubernetesResourceCount('deployment', 'mod-inventory-storage', namespace.getNamespaceName(), '4')
            sleep(time: 10, unit: 'SECONDS')
            kubectl.setKubernetesResourceCount('deployment', 'mod-search', namespace.getNamespaceName(), '4')

            folioHelm.checkDeploymentsRunning(namespace.getNamespaceName(), namespace.getModules().getBackendModules())
        }
      }
      int counter = 0
      retry(20) {
        sleep time: (counter == 0 ? 0 : 2), unit: 'MINUTES'
        counter++

        eureka.initializeFromScratch(
          namespace.getTenants()
          , namespace.getClusterName()
          , namespace.getNamespaceName()
          , namespace.getEnableConsortia()
          , true
          , false // Set this option true, when users & groups migration is required.
        )
      }
    }

      stage('[Rest] Configure edge') {
        retry(5) {
          new Edge(this, "${namespace.generateDomain('kong')}", "${namespace.generateDomain('keycloak')}").createEurekaUsers(namespace)
        }
      }

      if (args.uiBuild) {
        stage('Build and deploy UI') {
          Map branches = [:]
          namespace.getTenants().each { tenantId, tenant ->
            if (tenant.getTenantUi()) {
              branches[tenantId] = {
                boolean isECSBff = tenant.tenantId.startsWith("c")
                folioUI.buildAndDeploy(namespace, tenant, args.platform == PlatformType.EUREKA, namespace.getDomains()['kong'] as String
                  , namespace.getDomains()['keycloak'] as String, isECSBff)
              }
            }
          }
          parallel branches
        }
      }

      //TODO: Add adequate slack notification https://folio-org.atlassian.net/browse/RANCHER-1892
      stage('[Notify] Eureka') {
        if (args.dataset) {
          logger.warning("SUCCESS: Eureka ${args.clusterName}-${args.namespaceName} env successfully built!!!")
          slackSend(color: 'good', message: args.clusterName + "-" + args.namespaceName + (args.type == 'update' ? " env successfully updated\n" : " env successfully built\n") +
            "1. https://${namespace.generateDomain('fs09000000')} creds: folio:folio\n" +
            "2. https://${namespace.generateDomain('fs09000002')} creds: folio-plus:Folio-plus1\n" +
            "3. https://${namespace.generateDomain('fs09000003')} creds: folio-aqa:Folio-aqa1\n" +
            "4. https://${namespace.generateDomain('cs00000int')} creds: ECSAdmin:admin",
            channel: '#eureka-sprint-testing')
        } else {
          slackSend(color: 'good', message: args.clusterName + "-" + args.namespaceName + " env successfully built\n" +
            "1. https://${namespace.generateDomain('diku')}\n" +
            "2. https://${namespace.generateDomain('consortium')}\n" +
            "3. https://${namespace.generateDomain('consortium2')} (if was enabled)",
            channel: Constants.SLACK_CHANNEL)
        }
      }

//    stage('Deploy ldp') {
//      folioHelm.withKubeConfig(namespace.getClusterName()) {
//        folioHelmFlow.deployLdp(namespace)
//      }
//    }

    } catch (Exception e) {
      // currentBuild.description = e.getMessage()
      // currentBuild.result = 'FAILURE'
      //TODO: Add adequate slack notification https://folio-org.atlassian.net/browse/RANCHER-1892
      // slackSend(color: 'danger', message: args.clusterName + "-" + args.namespaceName + " env build failed...\n" + "Console output: ${env.BUILD_URL}", channel: args.dataset ? '#eureka-sprint-testing' : Constants.SLACK_CHANNEL)
      logger.error(e)
    }
  }
}
