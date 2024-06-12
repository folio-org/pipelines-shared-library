import org.folio.Constants
import org.folio.rest.Okapi
import org.folio.rest.model.LdpConfig
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.Logger
import org.folio.utilities.Tools
import org.folio.utilities.model.Project

/*
Deprecated method. Use folioTerraform.groovy
 */

void project(Project project_config, OkapiTenant tenant, String tf_work_dir, String tf_vars) {
  switch (project_config.getAction()) {
    case "apply":
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
        folioTerraform.withTerraformClient {
          folioTerraform.applyFlow {
            working_dir = tf_work_dir
            vars = tf_vars
            workspace_name = "${project_config.getClusterName()}-${project_config.getProjectName()}"
            if (project_config.getRestoreFromBackup() && project_config.getBackupType() == 'postgresql') {
              if (project_config.getBackupName()?.trim()) {
                preAction = {
                  stage('Restore DB') {
                    folioTerraform.tfPostgreSQLPlan(tf_work_dir, tf_vars ?: '')
                    folioTerraform.tfApply(tf_work_dir)
                    build job: Constants.JENKINS_JOB_RESTORE_PG_BACKUP,
                      parameters: [string(name: 'rancher_cluster_name', value: project_config.getClusterName()),
                                   string(name: 'rancher_project_name', value: project_config.getProjectName()),
                                   string(name: 'tenant_id_to_backup_modules_versions', value: tenant.getId()),
                                   booleanParam(name: 'restore_from_backup', value: project_config.getRestoreFromBackup()),
                                   string(name: 'backup_name', value: project_config.getBackupName())]
                  }
                }
              } else {
                new Logger(this, "folioDeploy").error("You've tried to restore PostgreSQL DB state from backup but didn't provide path/name of it.\nPlease, provide correct DB backup path/name and try again.")
              }
            }
          }
        }
      }
      break
    case "destroy":
      folioHelm.withK8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
        folioTools.deleteOpenSearchIndices(project_config.getClusterName(), project_config.getProjectName())
        folioTools.deleteKafkaTopics(project_config.getClusterName(), project_config.getProjectName())
      }
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
        folioTerraform.withTerraformClient {
          folioTerraform.destroyFlow {
            working_dir = tf_work_dir
            vars = tf_vars ?: ''
            workspace_name = "${project_config.getClusterName()}-${project_config.getProjectName()}"
          }
        }
      }
      break
    default:
      break
  }
}

void okapi(Project project_config) {
  String values_path = folioHelm.generateModuleValues('okapi', project_config.getTenant().getOkapiVersion(), project_config, project_config.getDomains().okapi)
  folioHelm.withK8sClient {
    awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
    folioHelm.addHelmRepository(Constants.FOLIO_HELM_V2_REPO_NAME, Constants.FOLIO_HELM_V2_REPO_URL, true)
    folioHelm.upgrade('okapi', project_config.getProjectName(), "${values_path}/okapi.yaml", Constants.FOLIO_HELM_V2_REPO_NAME, 'okapi')
  }
}

void backend(Map install_backend_map, Project project_config, Boolean custom_module = false, Boolean enable_rw_split = false) {
  folioHelm.withK8sClient {
    awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
    folioHelm.addHelmRepository(Constants.FOLIO_HELM_V2_REPO_NAME, Constants.FOLIO_HELM_V2_REPO_URL, true)
    install_backend_map.each { name, version ->
      if (name.startsWith("mod-")) {
        String values_path = folioHelm.generateModuleValues(name, version, project_config, '', custom_module, enable_rw_split)
        folioHelm.upgrade(name, project_config.getProjectName(), "${values_path}/${name}.yaml", Constants.FOLIO_HELM_V2_REPO_NAME, name)
      } else {
        new Logger(this, "folioDeploy").warning("${name} is not a backend module")
      }
    }
  }
}

void edge(Map install_edge_map, Project project_config, Boolean custom_module = false) {
  folioHelm.withK8sClient {
    awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
    folioHelm.addHelmRepository(Constants.FOLIO_HELM_V2_REPO_NAME, Constants.FOLIO_HELM_V2_REPO_URL, true)
    install_edge_map.each { name, version ->
      if (name.startsWith("edge-")) {
        String values_path = folioHelm.generateModuleValues(name, version, project_config, project_config.getDomains().edge, custom_module)
        folioHelm.upgrade(name, project_config.getProjectName(), "${values_path}/${name}.yaml", Constants.FOLIO_HELM_V2_REPO_NAME, name)
      } else {
        new Logger(this, "folioDeploy").warning("${name} is not an edge module")
      }
    }
  }
}

void uiBundle(String tenant_id, Project project_config) {
  String values_path = folioHelm.generateModuleValues('ui-bundle', project_config.getUiBundleTag(), project_config, project_config.getDomains().ui)
  folioHelm.withK8sClient {
    awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
    folioHelm.addHelmRepository(Constants.FOLIO_HELM_V2_REPO_NAME, Constants.FOLIO_HELM_V2_REPO_URL, true)
    folioHelm.upgrade("${tenant_id}-ui-bundle", project_config.getProjectName(), "${values_path}/ui-bundle.yaml", Constants.FOLIO_HELM_V2_REPO_NAME, 'platform-complete')
  }
}

void greenmail(Project project_config) {
  folioHelm.withK8sClient {
    awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
    folioHelm.addHelmRepository(Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.FOLIO_HELM_HOSTED_REPO_URL, false)
    folioHelm.upgrade("greenmail", project_config.getProjectName(), "''", Constants.FOLIO_HELM_HOSTED_REPO_NAME, "greenmail")
  }
}

void ldp_server(tenant, Project project_config, admin_user, superadmin_user, LdpConfig ldpConfig, String db_host, folio_db_password) {
  new Okapi(this, "https://${project_config.getDomains().okapi}", superadmin_user).configureLdpDbSettings(tenant, admin_user,
    new Tools(this).build_ldp_setting_json(project_config, admin_user as OkapiUser, "ldp_db_info.json.template", ldpConfig,
      db_host, "folio_modules", "postgres", folio_db_password))
  new Okapi(this, "https://${project_config.getDomains().okapi}", superadmin_user).configureLdpSavedQueryRepo(tenant, admin_user,
    new Tools(this).build_ldp_setting_json(project_config, admin_user as OkapiUser, "ldp_sqconfig.json.template", ldpConfig,
      db_host, "folio_modules", "postgres", folio_db_password))
  folioHelm.withK8sClient {
    awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())

    new Tools(this).createFileFromString("ldpconf.json", new Tools(this).build_ldp_setting_json(project_config, admin_user as OkapiUser, "ldp_ldpconf.json.template", ldpConfig,
      db_host, "folio_modules", "postgres", folio_db_password))
    kubectl.createConfigMap("ldpconf", project_config.getProjectName(), "./ldpconf.json")

    folioHelm.addHelmRepository(Constants.FOLIO_HELM_V2_REPO_NAME, Constants.FOLIO_HELM_V2_REPO_URL, true)
    folioHelm.upgrade("ldp-server", project_config.getProjectName(), "''", Constants.FOLIO_HELM_V2_REPO_NAME, "ldp-server")
  }
}
