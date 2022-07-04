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
//--version ${chart_version}
def helmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String db_backup_name) {
    stage('Helm install') {
        sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --set psql.projectNamespace=${project_namespace} \
        --set psql.jenkinsDbBackupName=${db_backup_name} --devel --namespace=${project_namespace} --wait --wait-for-jobs"
    }
}

def helmDelete(String build_id, String project_namespace) {
    stage('Helm delete') {
        sh "helm delete psql-dump-build-id-${build_id} --namespace=${project_namespace}"
    }
}
