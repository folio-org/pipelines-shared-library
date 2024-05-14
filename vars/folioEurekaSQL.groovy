import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.models.RancherNamespace
import org.folio.utilities.Logger
import org.folio.utilities.Tools

void initSQL(RancherNamespace namespace, List DBs = ['keycloak', 'kong'], Boolean type) {
  String arn = Constants.AWS_RDS_CLUSTER_ARN + namespace.getClusterName() + '-' + namespace.getNamespaceName()
  String secret = Constants.AWS_RDS_CLUSTER_SECRET
  Logger logger = new Logger(this, 'initSQL')
  new Tools(this).copyResourceFileToWorkspace("eureka/eureka_db.tpl")
  DBs.each { db ->
    String tpl = readFile file: "./eureka_db.tpl"
    LinkedHashMap data = [db_name: "${db}", db_username: "${if (db == 'keycloak') { 'keycloak_admin' } else { 'kong_admin' }}", db_password: Constants.PG_ROOT_DEFAULT_PASSWORD]
    writeFile encoding: 'utf-8', file: "${db}.sql", text: (new StreamingTemplateEngine().createTemplate(tpl).make(data)).toString()
    folioHelm.withKubeConfig(namespace.getClusterName()) {
      if (!type) {
        try {
          logger.info("Trying to init Eureka DBs on AWS RDS Cluster...")
          def sql = readFile file: "./${db}.sql"
          sql.each { sql_line ->
            sh(script: "aws --region ${Constants.AWS_REGION} rds-data execute-statement --resource-arn \"$arn\" --database  \"folio\" --secret-arn \"$secret\" --sql \"$sql_line\"", returnStdout: true)
          }
        } catch (Exception e) {
          logger.error("Error: ${e.getMessage()}")
        }
      } else {
        try {
          logger.info("Trying to init Eureka DBs on built-in PostgresSQL...")
          sh(script: "kubectl cp ./${db}.sql pod/postgresql-${namespace.getNamespaceName()}-0:/tmp/${db}.sql -n ${namespace.getNamespaceName()}", returnStdout: true)
          sh(script: "kubectl exec pod/postgresql-${namespace.getNamespaceName()}-0 -n ${namespace.getNamespaceName()} -- /opt/bitnami/postgresql/bin/psql -a -f /tmp/${db}.sql", returnStdout: true)
        } catch (Exception e) {
          logger.error("Error: ${e.getMessage()}")
        }
      }
    }
  }
}
