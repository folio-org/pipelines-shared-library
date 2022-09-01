def configureKubectl(String region, String cluster_name) {
    stage('Configure kubectl') {
        sh "aws eks update-kubeconfig --region ${region} --name ${cluster_name} > /dev/null"
    }
}

def configureHelm(String repo_name, String repo_url) {
    stage('Configure Helm') {
        sh "helm repo add ${repo_name} ${repo_url}"
    }
}


def backupHelmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String cluster_name, db_backup_name, String tenant_id_to_backup_modules_versions, String tenant_admin_username_to_backup_modules_versions, String tenant_admin_password_to_backup_modules_versions) {
    stage('Helm install') {
        sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --version ${chart_version} --set psql.projectNamespace=${project_namespace} \
        --set psql.jenkinsDbBackupName=${db_backup_name} --set psql.job.action='backup' --set psql.clusterName=${cluster_name} \
        --set psql.tenantBackupModulesForId=${tenant_id_to_backup_modules_versions} \
        --set psql.tenantBackupModulesForAdminUsername=${tenant_admin_username_to_backup_modules_versions} \
        --set psql.tenantBackupModulesForAdminPassword=${tenant_admin_password_to_backup_modules_versions} \
        --namespace=${project_namespace} --wait --wait-for-jobs"
    }
}

def restoreHelmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String db_backup_name) {
    stage('Helm install') {
        sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --version ${chart_version} --set psql.projectNamespace=${project_namespace} \
        --set psql.jenkinsDbBackupName=${db_backup_name} --set psql.job.action='restore' \
        --namespace=${project_namespace} --wait --wait-for-jobs"
    }
}

def helmDelete(String build_id, String project_namespace) {
    stage('Helm delete') {
        sh "helm delete psql-dump-build-id-${build_id} --namespace=${project_namespace}"
    }
}
