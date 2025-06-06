import org.folio.Constants
import org.folio.jenkins.PodTemplates
import org.folio.models.LdpConfig
import org.folio.models.TerraformConfig
import org.folio.models.parameters.CreateNamespaceParameters

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

void call(CreateNamespaceParameters args) {
  PodTemplates podTemplates = new PodTemplates(this, true)

  podTemplates.rancherAgent {
    println("Delete operation parameters:\n${prettyPrint(toJson(args))}")

    stage('Checkout') {
      checkout(poll: false,
        scm: scmGit(branches: [[name: "*/${folioTools.getPipelineBranch()}"]],
          extensions: [cloneOption(depth: 50, noTags: true, reference: '', shallow: true)],
          userRemoteConfigs: [[credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                               url          : "${Constants.FOLIO_GITHUB_URL}/pipelines-shared-library.git"]]))
    }

    LdpConfig ldpConfig = new LdpConfig()
    withCredentials([string(credentialsId: 'ldp-db-password', variable: 'LDP_DB_PASSWORD')]) {
      ldpConfig.setLdpAdminDbUserPassword(LDP_DB_PASSWORD)
    }
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
    tfConfig.addVar('pg_ldp_user_password', ldpConfig.getLdpAdminDbUserPassword())
    tfConfig.addVar('github_team_ids', folioTools.getGitHubTeamsIds("${Constants.ENVS_MEMBERS_LIST[args.namespaceName]},${args.members}").collect { "\"${it}\"" })

    stage('[AWS Parameter Store] cleanup') {
      awscli.withAwsClient {
        folioTools.deleteSSMParameters(args.clusterName, args.namespaceName)
      }
    }

    folioHelm.withKubeConfig(args.clusterName) {
      stage('[Helm uninstall] All') {
        folioHelm.deleteFolioModulesParallel(args.namespaceName)
      }
      retry(2) {
        if (args.opensearchType != 'built-in') {
          stage('[Kubectl] Cleanup opensearch indices') {
            folioTools.deleteOpenSearchIndices(args.clusterName, args.namespaceName)
          }
        }
        if (args.kafkaType != 'built-in') {
          stage('[Kubectl] Cleanup kafka topics') {
            folioTools.deleteKafkaTopics(args.clusterName, args.namespaceName)
          }
        }
      }
    }

    stage('[Terraform] Destroy') {
      folioTerraformFlow.manageNamespace('destroy', tfConfig)
    }
  }
}
