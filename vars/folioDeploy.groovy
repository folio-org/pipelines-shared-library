import org.folio.Constants
import org.folio.utilities.Logger

void project(Closure body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    switch (config.action) {
        case "apply":
            terraform.tfWrapper {
                terraform.tfApplyFlow {
                    working_dir = config.working_dir
                    workspace_name = "${config.cluster_name}-${config.project_name}"
                    tf_vars = config.tf_vars ?: ''
                    if (config.restore_backup) {
                        if (config.backup_name) {
                            preAction = {
                                stage('Restore DB') {
                                    terraform.tfPostgreSQLPlan(config.working_dir, config.tf_vars ?: '')
                                    terraform.tfApply(config.working_dir)
                                    build job: Constants.JENKINS_JOB_RESTORE_PG_BACKUP,
                                        parameters: [string(name: 'rancher_cluster_name', value: config.cluster_name),
                                                     string(name: 'rancher_project_name', value: config.project_name),
                                                     string(name: 'tenant_id_to_backup_modules_versions', value: config.target_tenant_id),
                                                     booleanParam(name: 'restore_postgresql_from_backup', value: config.restore_backup),
                                                     string(name: 'restore_postgresql_backup_name', value: config.backup_name)]
                                }
                            }
                        } else {
                            new Logger(this, "folioDeploy").error("You've tried to restore DB state from backup but didn't provide path/name of it.\nPlease, provide correct DB backup path/name and try again.")
                        }
                    }
                }
            }
            break
        case "destroy":
            terraform.tfWrapper {
                terraform.tfDestroyFlow {
                    working_dir = config.working_dir
                    workspace_name = "${config.cluster_name}-${config.project_name}"
                    tf_vars = config.tf_vars ?: ''
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
        helm.getKubeConfig(Constants.AWS_REGION, cluster_name)
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        helm.upgrade('okapi', project_name, "${values_path}/okapi.yaml", Constants.FOLIO_HELM_REPO_NAME, 'okapi')
    }
}

void backend(Map install_backend_map, def config, String cluster_name, String project_name) {
    helm.k8sClient {
        helm.getKubeConfig(Constants.AWS_REGION, cluster_name)
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
        helm.getKubeConfig(Constants.AWS_REGION, cluster_name)
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
    sh "cat ${values_path}/ui-bundle.yaml"
    helm.k8sClient {
        helm.getKubeConfig(Constants.AWS_REGION, cluster_name)
        helm.addRepo(Constants.FOLIO_HELM_REPO_NAME, Constants.FOLIO_HELM_REPO_URL)
        helm.upgrade("${tenant_id}-ui-bundle", project_name, "${values_path}/ui-bundle.yaml", Constants.FOLIO_HELM_REPO_NAME, 'platform-complete')
    }
}
