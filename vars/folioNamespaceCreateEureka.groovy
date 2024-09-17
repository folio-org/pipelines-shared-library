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

    println("folioNamespaceCreateEureka installJson: $installJson")
    println("folioNamespaceCreateEureka eurekaPlatform: $eurekaPlatform")

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

    println("folioNamespaceCreateEureka namespace.getModules(): ${namespace.getModules()}")

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


    Eureka eureka = new Eureka(this, namespace.generateDomain('kong'), namespace.generateDomain('keycloak'))

    //DO NOT REMOVE | FIX FOR DNS PROPAGATION ISSUE!!!
    timeout(time: 10, unit: 'MINUTES') {

      def check = false

      while (!check) {
        check = sh(script: "curl -fs https://${namespace.generateDomain('keycloak')}/admin/master/console/", returnStdout: true).trim()
      }

    }

    //Don't move from here because it increases Keycloak TTL before mgr modules to be deployed
    int counter_dns = 0
    retry(3) {
      sleep time: (counter_dns == 0 ? 0 : 1), unit: 'MINUTES'
      counter_dns++
      eureka.defineKeycloakTTL()
    }

    stage('[ASG] configure') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        def asg_json = sh(script: "aws autoscaling describe-auto-scaling-groups " +
          "--filters \"Name=tag:\"eks:cluster-name\",Values=${namespace.getClusterName()}\" " +
          "--region ${Constants.AWS_REGION}", returnStdout: true)
        writeJSON file: 'asg.json', json: asg_json
        def asg_data = readJSON file: './asg.json'
        sh(script: "aws autoscaling set-desired-capacity " +
          "--auto-scaling-group-name ${asg_data.AutoScalingGroups[0].AutoScalingGroupName} " +
          "--desired-capacity ${asg_data.AutoScalingGroups[0].DesiredCapacity + 1} " +
          "--region ${Constants.AWS_REGION}")
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

//      namespace.withApplications([
//        "app-platform-full": "app-platform-full-1.0.0-SNAPSHOT.660"
//        , "app-consortia": "app-consortia-1.0.0-SNAPSHOT.660"
//      ])
//
//      namespace.getTenants().values().each {tenant ->
//        if(tenant instanceof EurekaTenantConsortia)
//          tenant.setApplications([
//            "app-platform-full": "app-platform-full-1.0.0-SNAPSHOT.660"
//            , "app-consortia": "app-consortia-1.0.0-SNAPSHOT.660"
//          ])
//        else
//          tenant.setApplications([
//            "app-platform-full": "app-platform-full-1.0.0-SNAPSHOT.660"
//          ])
//      }
    }

    stage('[Helm] Deploy modules') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        println(namespace.getModules().getBackendModules())
        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getBackendModules())
      }
    }

    stage('[Rest] Configure edge') {
      folioEdge.renderEphemeralProperties(namespace)
//      edge.createEdgeUsers(namespace.getTenants()[namespace.getDefaultTenantId()]) TODO should be replaced with Eureka Edge Users.
    }

    stage('[Helm] Deploy edge') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        namespace.getModules().getEdgeModules().each { name, version -> kubectl.createConfigMap("${name}-ephemeral-properties", namespace.getNamespaceName(), "./${name}-ephemeral-properties")
        }
        retry(3) {
          folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getEdgeModules())
        }
      }
    }

    stage('[Rest] Initialize') {
      int counter = 0
      retry(10) {
        //The first wait time should be at leas 10 minutes due to module's long time instantiation
        sleep time: (counter == 0 ? 5 : 2), unit: 'MINUTES'
        counter++
        eureka.initializeFromScratch(namespace.getTenants(), namespace.getEnableConsortia())
      }
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
                                   hasAllPerms         : true,
                                   isSingleTenant      : true,
                                   tenantOptions       : """{${tenantId}: {name: "${tenantId}", clientId: "${tenantId}-application"}}""",
                                   tenantId            : ui.getTenantId(),
                                   custom_hash         : ui.getHash(),
                                   custom_url          : "https://${namespace.getDomains()['kong']}",
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
    }

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

