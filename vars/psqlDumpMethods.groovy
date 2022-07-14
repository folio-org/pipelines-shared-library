def installKubectl() {
    stage('Install kubectl') {
        sh "curl -sLO https://storage.googleapis.com/kubernetes-release/release/v1.22.9/bin/linux/amd64/kubectl && mv kubectl /usr/bin/kubectl && chmod +x /usr/bin/kubectl"
    }
}

def installHelm() {
    stage('Install Helm') {
        sh "apk add --update --no-cache curl ca-certificates && curl -sL https://get.helm.sh/helm-v3.9.0-linux-amd64.tar.gz | tar -xvz && mv linux-amd64/helm /usr/bin/helm && chmod +x /usr/bin/helm && rm -rf linux-amd64"
    }
}

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

/*--version ${chart_version}*/
def backupHelmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String db_backup_name) {
    stage('Helm install') {
        sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --devel --set psql.projectNamespace=${project_namespace} \
        --set psql.jenkinsDbBackupName=${db_backup_name} --namespace=${project_namespace} --wait --wait-for-jobs"
    }
}

def restoreHelmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String db_backup_name, String action) {
    stage('Helm install') {
        sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --devel --set psql.projectNamespace=${project_namespace} \
        --set psql.jenkinsDbBackupName=${db_backup_name} --set psql.job.action=${action} \
        --namespace=${project_namespace} --wait --wait-for-jobs"
    }
}

def helmDelete(String build_id, String project_namespace) {
    stage('Helm delete') {
        sh "helm delete psql-dump-build-id-${build_id} --namespace=${project_namespace}"
    }
}
