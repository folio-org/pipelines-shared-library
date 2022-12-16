import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.Logger
import org.folio.utilities.model.Project

void project(Project project_config, OkapiTenant tenant, Map tf) {
    switch (project_config.getAction()) {
        case "apply":
            terraform.tfWrapper {
                terraform.tfApplyFlow {
                    working_dir = tf.working_dir
                    tf_vars = tf.variables
                    workspace_name = "${project_config.getClusterName()}-${project_config.getProjectName()}"
                    if (project_config.getRestoreFromBackup() && project_config.getBackupType() == 'postgresql') {
                        if (project_config.getBackupName()?.trim()) {
                            preAction = {
                                stage('Restore DB') {
                                    terraform.tfPostgreSQLPlan(tf.working_dir, tf.variables ?: '')
                                    terraform.tfApply(tf.working_dir)
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
            break
        case "destroy":
            terraform.tfWrapper {
                terraform.tfDestroyFlow {
                    working_dir = tf.working_dir
                    tf_vars = tf.variables ?: ''
                    workspace_name = "${project_config.getClusterName()}-${project_config.getProjectName()}"
                }
            }
            break
        default:
            break
    }
}

void okapi(Project project_config) {
    String values_path = helm.generateModuleValues('okapi', project_config.getTenant().getOkapiVersion(), project_config, project_config.getDomains().okapi)
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        helm.upgrade('okapi', project_config.getProjectName(), "${values_path}/okapi.yaml", Constants.FOLIO_HELM_REPO_NAME, 'okapi')
    }
}

void backend(Map install_backend_map, Project project_config, Boolean custom_module = false) {
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        install_backend_map.each { name, version ->
            if (name.startsWith("mod-")) {
                String values_path = helm.generateModuleValues(name, version, project_config, '', custom_module)
                helm.upgrade(name, project_config.getProjectName(), "${values_path}/${name}.yaml", Constants.FOLIO_HELM_REPO_NAME, name)
            } else {
                new Logger(this, "folioDeploy").warning("${name} is not a backend module")
            }
        }
    }
}

void edge(Map install_edge_map, Project project_config, Boolean custom_module = false) {
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        install_edge_map.each { name, version ->
            if (name.startsWith("edge-")) {
                String values_path = helm.generateModuleValues(name, version, project_config, project_config.getDomains().edge, custom_module)
                helm.upgrade(name, project_config.getProjectName(), "${values_path}/${name}.yaml", Constants.FOLIO_HELM_REPO_NAME, name)
            } else {
                new Logger(this, "folioDeploy").warning("${name} is not an edge module")
            }
        }
    }
}

void uiBundle(String tenant_id, Project project_config) {
    String values_path = helm.generateModuleValues('ui-bundle', project_config.getUiBundleTag(), project_config, project_config.getDomains().ui)
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        helm.upgrade("${tenant_id}-ui-bundle", project_config.getProjectName(), "${values_path}/ui-bundle.yaml", Constants.FOLIO_HELM_REPO_NAME, 'platform-complete')
    }
}

void greenmail(Project project_config) {
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, project_config.getClusterName())
        helm.addRepo(Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.FOLIO_HELM_HOSTED_REPO_URL)
        helm.upgrade_greenmail("greenmail", project_config.getProjectName(), Constants.FOLIO_HELM_HOSTED_REPO_NAME, "greenmail")
    }
}
