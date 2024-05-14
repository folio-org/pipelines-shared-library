import com.cloudbees.groovy.cps.NonCPS
import org.folio.Constants
import org.folio.models.Index
import org.folio.models.InstallRequestParams
import org.folio.models.OkapiTenantConsortia
import org.folio.models.RancherNamespace
import org.folio.models.TenantUi
import org.folio.models.TerraformConfig
import org.folio.models.parameters.CreateNamespaceParameters
import org.folio.rest.GitHubUtility
import org.folio.rest_v2.Edge
import org.folio.rest_v2.Main

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
    TenantUi tenantUi = new TenantUi("${namespace.getClusterName()}-${namespace.getNamespaceName()}",
      commitHash, args.folioBranch)
    InstallRequestParams installRequestParams = new InstallRequestParams()
      .withTenantParameters("loadReference=${args.loadReference},loadSample=${args.loadSample}")

    namespace.withSuperTenantAdminUser().withOkapiVersion(args.okapiVersion).withDefaultTenant(defaultTenantId)
      .withDeploymentConfigType(args.configType)
    namespace.setEnableRwSplit(args.rwSplit)
    namespace.addDeploymentConfig(folioTools.getPipelineBranch())
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

    if (args.eureka) {
      folioEurekaSQL.initSQL(namespace,"")
    }

    Main main = new Main(this, namespace.getDomains()['okapi'], namespace.getSuperTenant(), true)
    Edge edge = new Edge(this, namespace.getDomains()['okapi'])

    stage('[Helm] Deploy Okapi') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        folioHelm.deployFolioModule(namespace, 'okapi', namespace.getOkapiVersion())
        folioHelm.checkPodRunning(namespace.getNamespaceName(), 'okapi')
      }
    }

    stage('[Rest] Okapi healthcheck') {
      sleep time: 3, unit: 'MINUTES'
      println("https://${namespace.getDomains()['okapi']}/_/proxy/health")
      common.healthCheck("https://${namespace.getDomains()['okapi']}/_/proxy/health")
    }

    stage('[Rest] Preinstall') {
      main.preInstall(namespace.getModules().getInstallJson(), namespace.getModules().getDiscoveryList())
    }

    stage('[Helm] Deploy backend') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getBackendModules())
        folioHelm.checkAllPodsRunning(namespace.getNamespaceName())
      }
    }

    stage('[Rest] Initialize') {
      sleep time: 10, unit: 'MINUTES'
      main.initializeFromScratch(namespace.getTenants(), namespace.getEnableConsortia())
    }

    stage('[Rest] Configure edge') {
      folioEdge.renderEphemeralProperties(namespace)
      edge.createEdgeUsers(namespace.getTenants()[namespace.getDefaultTenantId()])
    }

    stage('[Helm] Deploy edge') {
      folioHelm.withKubeConfig(namespace.getClusterName()) {
        namespace.getModules().getEdgeModules().each { name, version ->
          kubectl.createConfigMap("${name}-ephemeral-properties", namespace.getNamespaceName(), "./${name}-ephemeral-properties")
        }
        folioHelm.deployFolioModulesParallel(namespace, namespace.getModules().getEdgeModules())
      }
    }

    stage('Build and deploy UI') {
      Map branches = [:]
      namespace.getTenants().each { tenantId, tenant ->
        if (tenant.getTenantUi()) {
          TenantUi ui = tenant.getTenantUi()
          branches[tenantId] = {
            def jobParameters = [
              tenant_id  : ui.getTenantId(),
              custom_hash: ui.getHash(),
              custom_url : "https://${namespace.getDomains()['okapi']}",
              custom_tag : ui.getTag(),
              consortia  : tenant instanceof OkapiTenantConsortia
            ]
            uiBuild(jobParameters, releaseVersion)
            folioHelm.withKubeConfig(namespace.getClusterName()) {
              folioHelm.deployFolioModule(namespace, 'ui-bundle', ui.getTag(), false, ui.getTenantId())
            }
          }
        }
      }
      parallel branches
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
