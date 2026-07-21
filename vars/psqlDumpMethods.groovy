import groovy.json.JsonOutput
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


def backupHelmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String cluster_name, db_backup_name, String psql_dump_backups_bucket_name, String postgresql_backups_directory, String timeout = '360m') {
  stage('Helm install') {
    sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --version ${chart_version} --set psql.projectNamespace=${project_namespace} \
        --set psql.dbBackupName=${db_backup_name} --set psql.job.action='backup' --set psql.clusterName=${cluster_name} \
        --set psql.s3BackupsBucketName=${psql_dump_backups_bucket_name} \
        --set psql.s3BackupsBucketDirectory=${postgresql_backups_directory} \
        --namespace=${project_namespace} --timeout ${timeout} --wait --wait-for-jobs"
  }
}

def restoreHelmInstall(String build_id, String repo_name, String chart_name, String chart_version, String project_namespace, String db_backup_name, String psql_dump_backups_bucket_name, String postgresql_backups_directory) {
  stage('Helm install') {
    sh "helm install psql-dump-build-id-${build_id} ${repo_name}/${chart_name} --version ${chart_version} --set psql.projectNamespace=${project_namespace} \
        --set psql.dbBackupName=${db_backup_name} --set psql.job.action='restore' \
        --set psql.s3BackupsBucketName=${psql_dump_backups_bucket_name} \
        --set psql.s3BackupsBucketDirectory=${postgresql_backups_directory} \
        --namespace=${project_namespace} --timeout 360m --wait --wait-for-jobs"
  }
}

def restoreHelmData(String repo_name, String chart_name, String chart_version, String db_backup_name, String db_backup_data, String bucket_name, String backups_directory, String namespace) {
  stage('[Restore data]') {
    folioHelm.addHelmRepository("${repo_name}", Constants.NEXUS_BASE_URL + "/${repo_name}/", true)
    try {
      sh "helm install psql-dump ${repo_name}/${chart_name} --version ${chart_version} \
        --set psql.dbBackupName=${db_backup_name} \
        --set psql.s3BackupsBucketName=${bucket_name} \
        --set psql.dbBackupData=${db_backup_data} \
        --set psql.s3BackupsBucketDirectory=${backups_directory} \
        --set psql.projectNamespace=${namespace} \
        --namespace=${namespace} --timeout 360m --wait --wait-for-jobs"
    } catch (Error error) {
      folioPrint.colored("Helm psql dump restore failed, error: ${error.getMessage()}", "red")
    }
    finally {
      folioPrint.colored("Performing helm chart ${chart_name}:${chart_version} uninstall operation...", "green")
      sh "helm uninstall psql-dump --namespace=${namespace}"
    }
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

// ===================================================================
// Standardized tenant/entitlements metadata methods (RANCHER-818)
// Used by the dumpDb pipeline to save Kong tenant metadata alongside
// PostgreSQL backups in the S3 bucket.
// Structure:
//   s3://<bucket>/<directory>/<backup-name>/
//     <backup-name>-tenants.json
//     <backup-name>-<tenant>-apps.json  (per tenant)
//     <cluster>-<namespace>-folioDB.psql  (created by helm chart)
// ===================================================================

/**
 * Derive the Kong API URL from cluster and namespace names.
 * Returns: https://<cluster>-<namespace>-kong.ci.folio.org
 */
def getKongUrl(String cluster, String namespace) {
  return "https://${cluster}-${namespace}-kong.${Constants.CI_ROOT_DOMAIN}"
}

/**
 * Read PostgreSQL database connection parameters from the db-credentials
 * K8s secret in the target namespace.
 *
 * Requires kubectl to be configured for the target cluster.
 *
 * @param namespace  Namespace that contains the db-credentials secret
 * @return Map with keys: host, port, db, user, password
 */
def getDBCredentials(String namespace) {
  return [
    host    : kubectl.getSecretValue(namespace, 'db-credentials', 'DB_HOST'),
    port    : kubectl.getSecretValue(namespace, 'db-credentials', 'DB_PORT'),
    db      : kubectl.getSecretValue(namespace, 'db-credentials', 'DB_DATABASE'),
    user    : kubectl.getSecretValue(namespace, 'db-credentials', 'DB_USERNAME'),
    password: kubectl.getSecretValue(namespace, 'db-credentials', 'DB_PASSWORD')
  ]
}

/**
 * Build a PostgreSQL connection URI from db-credentials.
 * Returns: postgres://user:password@host:port/db?sslmode=disable
 */
def buildPsqlConnectionUri(Map dbCredentials) {
  return "postgres://${dbCredentials.user}:${dbCredentials.password}@${dbCredentials.host}:${dbCredentials.port}/${dbCredentials.db}?sslmode=disable"
}

/**
 * Fetch all tenants from the Kong API (endpoint is open, no auth needed).
 * @param kongUrl  Base Kong URL e.g. https://folio-etesting-snapshot-kong.ci.folio.org
 * @return Parsed JSON object with a "tenants" array
 */
def fetchTenants(String kongUrl) {
  String tenantsUrl = "${kongUrl}/tenants?limit=500"
  def resp = sh(script: "curl -s '${tenantsUrl}'", returnStdout: true).trim()
  return readJSON(text: resp)
}

/**
 * Fetch entitlements (entitled applications) for a specific tenant from the Kong API
 * (endpoint is open, no auth needed).
 * @param kongUrl   Base Kong URL
 * @param tenantId  UUID of the tenant
 * @return Parsed JSON response containing entitlements
 */
def fetchTenantEntitlements(String kongUrl, String tenantId) {
  String url = "${kongUrl}/entitlements?limit=500&query=tenantId==${tenantId}"
  def resp = sh(script: "curl -s '${url}'", returnStdout: true).trim()
  return readJSON(text: resp)
}

/**
 * Save the tenants list as a JSON file to S3 under the backup directory.
 * File:  <backup-name>-tenants.json
 * Path:  s3://<bucket>/<directory>/<backup-name>/<backup-name>-tenants.json
 */
def saveTenantsMetadataToS3(String s3Bucket, String s3Directory, String backupName, String tenantsJson) {
  stage('Upload tenants metadata to S3') {
    String fileName = "${backupName}-tenants.json"
    writeFile(file: fileName, text: tenantsJson)
    sh "aws s3 cp ${fileName} s3://${s3Bucket}/${s3Directory}/${backupName}/${fileName}"
    sh "rm -f ${fileName}"
    echo "Uploaded tenant list: s3://${s3Bucket}/${s3Directory}/${backupName}/${fileName}"
  }
}

/**
 * Fetch entitlements for every tenant and save as per-tenant JSON files to S3.
 * Each file:  <backup-name>-<tenantName>-apps.json
 * Path:       s3://<bucket>/<directory>/<backup-name>/<backup-name>-<tenantName>-apps.json
 */
def saveEntitlementsMetadataToS3(String s3Bucket, String s3Directory, String backupName, String kongUrl, List tenants) {
  stage('Upload entitlements metadata to S3') {
    tenants.each { tenant ->
      String tenantId = tenant.id
      String tenantName = tenant.name

      echo "Fetching entitlements for tenant: ${tenantName} (${tenantId})"
      def entitlementsJson = fetchTenantEntitlements(kongUrl, tenantId)

      String fileName = "${backupName}-${tenantName}-apps.json"
      String content = JsonOutput.toJson(entitlementsJson)

      writeFile(file: fileName, text: content)
      sh "aws s3 cp ${fileName} s3://${s3Bucket}/${s3Directory}/${backupName}/${fileName}"
      sh "rm -f ${fileName}"
      echo "Uploaded: s3://${s3Bucket}/${s3Directory}/${backupName}/${fileName}"
    }
  }
}
