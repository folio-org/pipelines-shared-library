import org.folio.Constants
import org.folio.models.TerraformConfig

void manageCluster(String action, TerraformConfig config) {
  folioTerraform.client {
    _clusterCredentials {
      switch (action) {
        case 'apply':
          apply(config, true)
          break
        case 'destroy':
          Closure preAction = {
            try {
              folioTerraform.removeFromState(config.getWorkDir(), 'elasticstack_elasticsearch_index_lifecycle.index_policy')
              folioTerraform.removeFromState(config.getWorkDir(), 'rancher2_catalog_v2.metrics-server[0]')
              folioTerraform.removeFromState(config.getWorkDir(), 'rancher2_catalog_v2.prometheus-community[0]')
            } catch (e) {
              println(e.getMessage())
            }
          }
          destroy(config, true, preAction)
          break
      }
    }
  }
}

void manageNamespace(String action, TerraformConfig config) {
  folioTerraform.client {
    _namespaceCredentials {
      switch (action) {
        case 'apply':
          apply(config)
          break
        case 'destroy':
          destroy(config)
          break
      }
    }
  }
}


void apply(TerraformConfig config, boolean approveRequired = false, Closure preAction = {}, Closure postAction = {}) {

  folioTerraform.init(config.getWorkDir())
  folioTerraform.selectWorkspace(config.getWorkDir(), config.getWorkspace())
  folioTerraform.statePull(config.getWorkDir())

  preAction.call()

  def attempts = 0
  retry(2) {
    if (attempts > 0) {
      sleep(60)
    }
    folioTerraform.plan(config.getWorkDir(), config.getVarsAsString())
    if (approveRequired) {
      folioTerraform.planApprove(config.getWorkDir())
    }
    folioTerraform.apply(config.getWorkDir())
    attempts++
  }

  postAction.call()
}

void destroy(TerraformConfig config, boolean approveRequired = false, Closure preAction = {}, Closure postAction = {}) {

  folioTerraform.init(config.getWorkDir())
  folioTerraform.selectWorkspace(config.getWorkDir(), config.getWorkspace())
  folioTerraform.statePull(config.getWorkDir())

  if (approveRequired) {
    input message: "Are you shure that you want to destroy ${config.getWorkspace()} cluster?"
  }

  preAction.call()

  folioTerraform.destroy(config.getWorkDir(), config.getVarsAsString())

  postAction.call()
}

private void _namespaceCredentials(Closure body) {
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.AWS_S3_SERVICE_ACCOUNT_ID,
                    accessKeyVariable: 'TF_VAR_s3_access_key',
                    secretKeyVariable: 'TF_VAR_s3_secret_key'],
                   [$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.AWS_S3_POSTGRES_BACKUPS,
                    accessKeyVariable: 'TF_VAR_s3_postgres_backups_access_key',
                    secretKeyVariable: 'TF_VAR_s3_postgres_backups_secret_key'],
                   usernamePassword(credentialsId: Constants.DOCKER_FOLIO_REPOSITORY_CREDENTIALS_ID,
                     passwordVariable: 'TF_VAR_folio_docker_registry_password',
                     usernameVariable: 'TF_VAR_folio_docker_registry_username')]) {
    body()
  }
}

private void _clusterCredentials(Closure body) {
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.AWS_CREDENTIALS_ID,
                    accessKeyVariable: 'TF_VAR_aws_access_key_id',
                    secretKeyVariable: 'TF_VAR_aws_secret_access_key'],
                   string(credentialsId: Constants.KUBECOST_LICENSE_KEY, variable: 'TF_VAR_kubecost_licence_key'),
                   string(credentialsId: Constants.SLACK_WEBHOOK_URL, variable: 'TF_VAR_slack_webhook_url')]) {
    body()
  }
}
