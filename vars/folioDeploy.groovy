import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.Logger
import org.folio.utilities.model.Project

void project(Project project_model, OkapiTenant tenant, Map tf) {
    switch (project_model.getAction()) {
        case "apply":
            terraform.tfWrapper {
                terraform.tfApplyFlow {
                    working_dir = tf.working_dir
                    tf_vars = tf.variables
                    workspace_name = "${project_model.getClusterName()}-${project_model.getProjectName()}"
                    if (project_model.getRestoreFromBackup() && project_model.getBackupType() == 'postgresql') {
                        if (project_model.getBackupName()?.trim()) {
                            preAction = {
                                stage('Restore DB') {
                                    terraform.tfPostgreSQLPlan(tf.working_dir, tf.variables ?: '')
                                    terraform.tfApply(tf.working_dir)
                                    build job: Constants.JENKINS_JOB_RESTORE_PG_BACKUP,
                                        parameters: [string(name: 'rancher_cluster_name', value: project_model.getClusterName()),
                                                     string(name: 'rancher_project_name', value: project_model.getProjectName()),
                                                     string(name: 'tenant_id_to_backup_modules_versions', value: tenant.getId()),
                                                     booleanParam(name: 'restore_from_backup', value: project_model.getRestoreFromBackup()),
                                                     string(name: 'restore_backup_name', value: project_model.getBackupName())]
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
                    workspace_name = "${project_model.getClusterName()}-${project_model.getProjectName()}"
                }
            }
            break
        default:
            break
    }
}

void okapi(def config, String version, String cluster_name, String project_name, String domain) {
    String values_path = helm.generateModuleValues(config, 'okapi', version, cluster_name, project_name, domain)
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, cluster_name)
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        helm.upgrade('okapi', project_name, "${values_path}/okapi.yaml", Constants.FOLIO_HELM_REPO_NAME, 'okapi')
    }
}

void backend(Map install_backend_map, def config, String cluster_name, String project_name) {
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, cluster_name)
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        install_backend_map.each { name, version ->
            if (name.startsWith("mod-")) {
                String values_path = helm.generateModuleValues(config, name, version, cluster_name, project_name)
                helm.upgrade(name, project_name, "${values_path}/${name}.yaml", Constants.FOLIO_HELM_REPO_NAME, name)
            } else {
                new Logger(this, "folioDeploy").warning("${name} is not a backend module")
            }
        }
    }
}

void edge(Map install_edge_map, def config, String cluster_name, String project_name, String domain) {
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, cluster_name)
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        install_edge_map.each { name, version ->
            if (name.startsWith("edge-")) {
                String values_path = helm.generateModuleValues(config, name, version, cluster_name, project_name, domain)
                helm.upgrade(name, project_name, "${values_path}/${name}.yaml", Constants.FOLIO_HELM_REPO_NAME, name)
            } else {
                new Logger(this, "folioDeploy").warning("${name} is not an edge module")
            }
        }
    }
}

void uiBundle(String tenant_id, def config, String version, String cluster_name, String project_name, String domain) {
    String values_path = helm.generateModuleValues(config, 'ui-bundle', version, cluster_name, project_name, domain)
    helm.k8sClient {
        awscli.getKubeConfig(Constants.AWS_REGION, cluster_name)
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        helm.upgrade("${tenant_id}-ui-bundle", project_name, "${values_path}/ui-bundle.yaml", Constants.FOLIO_HELM_REPO_NAME, 'platform-complete')
    }
}
