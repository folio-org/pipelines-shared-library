import org.folio.Constants
import org.folio.models.*
import org.folio.models.parameters.CreateNamespaceParameters
import org.folio.rest.GitHubUtility
import org.folio.rest_v2.eureka.Eureka
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
    tfConfig.addVar('eureka', args.eureka)

    //TODO: Remove it via ticket https://folio-org.atlassian.net/browse/RANCHER-1893
    if (args.clusterName in ['folio-dev', 'folio-tmp', 'folio-testing', 'folio-perf']) {
      folioPrint.colored("ERROR: Target cluster IS NOT EUREKA!", 'red')
      currentBuild.result = 'ABORTED'
      return
    }

    stage('[Terraform] Provision') {
      folioTerraformFlow.manageNamespace('apply', tfConfig)
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
    def eurekaPlatform = new GitHubUtility(this).getEurekaList(folioRepository, args.folioBranch)
    installJson.addAll(eurekaPlatform)

    TenantUi tenantUi = new TenantUi("${namespace.getClusterName()}-${namespace.getNamespaceName()}",
      commitHash, args.folioBranch)

    EurekaRequestParams installRequestParams = new EurekaRequestParams()
      .withIgnoreErrors(true)
      .doLoadReference(args.loadReference)
      .doLoadSample(args.loadSample) as EurekaRequestParams

    namespace.withSuperTenantAdminUser().withOkapiVersion(args.okapiVersion).withDefaultTenant(defaultTenantId)
      .withDeploymentConfigType(args.configType)

    namespace.setEnableRtr(args.rtr)
    namespace.addDeploymentConfig(folioTools.getPipelineBranch())
    namespace.getModules().setInstallJson(installJson)

    //TODO: Temporary solution. Unused by Eureka modules have been removed.
    namespace.getModules().removeModule('mod-login')
    namespace.getModules().removeModule('mod-authtoken')

    namespace.addTenant(
      folioDefault.tenants()[namespace.getDefaultTenantId()]
        .convertTo(EurekaTenant.class)
        .withAWSSecretStoragePathName("${namespace.getClusterName()}-${namespace.getNamespaceName()}")
        .withInstallJson(namespace.getModules().getInstallJson().collect())
        .withIndex(new Index('instance', true, true))
        .withIndex(new Index('authority', true, false))
        .withInstallRequestParams(installRequestParams.clone())
        .withTenantUi(tenantUi.clone())
    )

    if (args.consortia) {
      namespace.setEnableConsortia(true, releaseVersion)

      DTO.convertMapTo(folioDefault.consortiaTenants([], installRequestParams), EurekaTenantConsortia.class)
        .values().each { tenant ->
        tenant.withInstallJson(namespace.getModules().getInstallJson())
          .withAWSSecretStoragePathName("${namespace.getClusterName()}-${namespace.getNamespaceName()}")

        if (tenant.getIsCentralConsortiaTenant()) {
          tenant.withTenantUi(tenantUi.clone())
//          tenant.okapiConfig.setLdpConfig(ldpConfig)
        }

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

    // TODO: Below [ASG] stage could be moved to one the shared libs and called with an appropriate parameters.
    stage('[ASG] configure') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        def nodes_before = sh(script: "kubectl get nodes --no-headers | wc -l", returnStdout: true).trim()

        def asg_json = sh(script: "aws autoscaling describe-auto-scaling-groups " +
          "--filters \"Name=tag:\"eks:cluster-name\",Values=${namespace.getClusterName()}\" " +
          "--region ${Constants.AWS_REGION}", returnStdout: true)
        writeJSON file: 'asg.json', json: asg_json
        def asg_data = readJSON file: './asg.json'
        sh(script: "aws autoscaling set-desired-capacity " +
          "--auto-scaling-group-name ${asg_data.AutoScalingGroups[0].AutoScalingGroupName} " +
          "--desired-capacity ${asg_data.AutoScalingGroups[0].DesiredCapacity + 1} " +
          "--region ${Constants.AWS_REGION}")

        //Make sure that the new node has joined target EKS cluster
        def nodes_after = sh(script: "kubectl get nodes --no-headers | wc -l", returnStdout: true).trim()

        while (nodes_before.toInteger() == nodes_after.toInteger()) {
          logger.debug("New worker node is joining to cluster: ${namespace.getClusterName()}...")
          nodes_after = sh(script: "kubectl get nodes --no-headers | wc -l", returnStdout: true).trim()
          sleep time: 10, unit: "SECONDS"
        }
      }
    }

    stage('[Helm] Deploy mgr-*') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getMgrModules())
      }
    }

    stage('[Rest] Preinstall') {
      namespace.withApplications(
        eureka.registerApplicationsFlow(
          args.consortia ? eureka.CURRENT_APPLICATIONS : eureka.CURRENT_APPLICATIONS_WO_CONSORTIA
          , namespace.getModules()
          , namespace.getTenants().values() as List<EurekaTenant>
        )
      )

      eureka.registerModulesFlow(
        namespace.getModules()
        , namespace.getApplications()
        , namespace.getTenants().values() as List<EurekaTenant>
      )
    }

    stage('[Helm] Deploy modules') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        logger.info(namespace.getModules().getBackendModules())

        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getBackendModules())
      }
    }

    stage('[Helm] Deploy edge') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
//        folioEdge.renderEphemeralPropertiesEureka(namespace)
        folioEdge.renderEphemeralProperties(namespace)
        namespace.getModules().getEdgeModules().each { name, version ->
          kubectl.createConfigMap("${name}-ephemeral-properties", namespace.getNamespaceName(), "./${name}-ephemeral-properties")
        }

        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getEdgeModules())
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
            TenantUi ui = tenant.getTenantUi()
            branches[tenantId] = {
              def jobParameters = [eureka              : args.eureka,
                                   kongUrl             : "https://${namespace.getDomains()['kong']}",
                                   keycloakUrl         : "https://${namespace.getDomains()['keycloak']}",
                                   tenantUrl           : "https://${namespace.generateDomain(tenantId)}",
                                   hasAllPerms         : false,
                                   isSingleTenant      : true,
                                   tenantOptions       : """{${tenantId}: {name: "${tenantId}", clientId: "${tenantId}-application"}}""",
                                   tenantId            : ui.getTenantId(),
                                   custom_hash         : ui.getHash(),
                                   custom_url          : "${namespace.getDomains()['kong']}", //Used for CNAME Record creation, DON'T CHANGE!!!
                                   custom_tag          : ui.getTag(),
                                   consortia           : tenant instanceof EurekaTenantConsortia,
                                   clientId            : ui.getTenantId() + "-application",
                                   rancher_cluster_name: namespace.getClusterName(),
                                   rancher_project_name: namespace.getNamespaceName()]

              uiBuild(jobParameters, releaseVersion)
              folioHelm.withKubeConfig(namespace.getClusterName()) {
                folioHelm.deployFolioModule(namespace, 'ui-bundle', ui.getTag(), false, ui.getTenantId())
              }
            }
          }
        }
        parallel branches
      }
      def cmd = "aws route53 change-resource-record-sets --hosted-zone-id ${Constants.HOSTED_ZONE_ID} --change-batch " +
        "'{\"Changes\":[{\"Action\":\"CREATE\",\"ResourceRecordSet\":{\"Name\":\"ecs-${namespace.getDomains()['kong']}.\",\"Type\":\"CNAME\",\"TTL\":60," +
        "\"ResourceRecords\":[{\"Value\":\"${namespace.getDomains()['kong']}.\"}]}}]}'\n"
      try {
        awscli.withAwsClient() {
          sh(script: cmd, returnStdout: true)
        }
      } catch (ignored) {
        logger.warning("Unable to create CNAME record for ${namespace.getDomains()['kong']}\nRecord already exists?")
      }

    }

    //TODO: Add adequate slack notification https://folio-org.atlassian.net/browse/RANCHER-1892
    stage('[Notify] Eureka') {
      slackSend(color: 'good', message: 'eureka-snapshot env successfully built\n' + "1. https://${namespace.generateDomain('diku')}\n" +
        "2. https://${namespace.generateDomain('consortium')}", channel: '#rancher_tests_notifications')
    }

//    stage('Deploy ldp') {
//      folioHelm.withKubeConfig(namespace.getClusterName()) {
//        folioHelmFlow.deployLdp(namespace)
//      }
//    }

  } catch (Exception e) {
    println(e)
    //TODO: Add adequate slack notification https://folio-org.atlassian.net/browse/RANCHER-1892
    slackSend(color: 'danger', message: "eureka-snapshot env build failed...\n" + "${env.BUILD_URL}", channel: '#rancher_tests_notifications')
    throw new Exception(e)
  }
}

