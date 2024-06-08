import org.folio.Constants
import org.folio.models.TerraformConfig
import org.folio.models.parameters.CreateNamespaceParameters

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

void call(CreateNamespaceParameters args) {
  println("Delete operation parameters:\n${prettyPrint(toJson(args))}")

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

  folioHelm.withKubeConfig(args.clusterName) {
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
