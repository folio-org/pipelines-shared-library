import org.folio.Constants

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


def backupHelmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String cluster_name, db_backup_name, String tenant_id_to_backup_modules_versions, String tenant_admin_username_to_backup_modules_versions, String tenant_admin_password_to_backup_modules_versions, String psql_dump_backups_bucket_name, String postgresql_backups_directory) {
    stage('Helm install') {
        sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --version ${chart_version} --set psql.projectNamespace=${project_namespace} \
        --set psql.dbBackupName=${db_backup_name} --set psql.job.action='backup' --set psql.clusterName=${cluster_name} \
        --set psql.tenantBackupModulesForId=${tenant_id_to_backup_modules_versions} \
        --set psql.tenantBackupModulesForAdminUsername=${tenant_admin_username_to_backup_modules_versions} \
        --set psql.tenantBackupModulesForAdminPassword=${tenant_admin_password_to_backup_modules_versions} \
        --set psql.s3BackupsBucketName=${psql_dump_backups_bucket_name} \
        --set psql.s3BackupsBucketDirectory=${postgresql_backups_directory} \
        --namespace=${project_namespace} --timeout 240m --wait --wait-for-jobs"
    }
}

def restoreHelmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String db_backup_name, String psql_dump_backups_bucket_name, String postgresql_backups_directory) {
    stage('Helm install') {
        sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --version ${chart_version} --set psql.projectNamespace=${project_namespace} \
        --set psql.dbBackupName=${db_backup_name} --set psql.job.action='restore' \
        --set psql.s3BackupsBucketName=${psql_dump_backups_bucket_name} \
        --set psql.s3BackupsBucketDirectory=${postgresql_backups_directory} \
        --namespace=${project_namespace} --timeout 240m --wait --wait-for-jobs"
    }
}

def restoreHelmDump(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String db_backup_data, String db_backup_name, String psql_dump_backups_bucket_name, String postgresql_backups_directory) {
  stage('Helm install') {
    sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --version ${chart_version} --set psql.projectNamespace=${project_namespace} \
        --set psql.dbBackupName=${db_backup_name} --set psql.job.action='restore' \
        --set psql.dbBackupData=${db_backup_data} \
        --set psql.s3BackupsBucketName=${psql_dump_backups_bucket_name} \
        --set psql.s3BackupsBucketDirectory=${postgresql_backups_directory} \
        --namespace=${project_namespace} --timeout 240m --wait --wait-for-jobs"
  }
}

def helmDelete(String build_id, String project_namespace) {
    stage('Helm delete') {
        sh "helm delete psql-dump-build-id-${build_id} --namespace=${project_namespace}"
    }
}

def savePlatformCompleteImageTag(String project_namespace, String db_backup_name, String s3_postgres_backups_bucket_name, String postgresql_backups_directory, String tenant_id) {
    stage('Save platform complete image tag') {
        sh "PLATFORM_COMPLETE_POD_LIST=\$(kubectl get pods --no-headers=true -o custom-columns=NAME_OF_MY_POD:.metadata.name -n ${project_namespace} | \
        grep platform-complete); for IMAGE in \$PLATFORM_COMPLETE_POD_LIST; \
        do IMAGE_TAG=\$(kubectl get pod \$IMAGE -n ${project_namespace} -o jsonpath='{.spec.containers[*].image}' | \
        sed 's/.*://' | grep .*-${tenant_id}-.*);if [ ! -z \$IMAGE_TAG  ];then break;fi;done; \
        echo \$IMAGE_TAG > ${db_backup_name}-image-tag.txt; aws s3 cp ${db_backup_name}-image-tag.txt ${s3_postgres_backups_bucket_name}/${postgresql_backups_directory}/${db_backup_name}/"
    }
}
