import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.models.RancherNamespace
import org.folio.utilities.Logger
import org.folio.utilities.Tools
import groovy.json.JsonSlurperClassic

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
      String pgadmin_pod = sh(script: "kubectl get pod -n ${namespace.getNamespaceName()} --no-headers | grep pgadmin | awk '{print \$1}'", returnStdout: true)
      sh(script: "kubectl cp ./${db}.sql pod/${pgadmin_pod}:/tmp/${db}.sql -n ${namespace.getNamespaceName()}", returnStdout: true)
      def connInfo = sh(script: "kubectl exec pod/${pgadmin_pod} -n ${namespace.getNamespaceName()} -- /bin/cat /pgadmin4/servers.json", returnStdout: true)
      def connStr = new JsonSlurperClassic().parseText("${connInfo}")
      if (!type) {
        try {
          logger.info("Trying to init Eureka DBs on AWS RDS Cluster...")
          sh(script: "kubectl exec pod/${pgadmin_pod} -- export PGPASSWORD=${Constants.PG_ROOT_DEFAULT_PASSWORD};/usr/local/pgsql-13/psql -h ${connStr["Servers"]["pg"]["Host"]} -p \"5432\" -u \"postgres\" -a -f /tmp/${db}.sql", returnStdout: true)
        } catch (Exception e) {
          logger.error("Error: ${e.getMessage()}")
        }
      } else {
        try {
          logger.info("Trying to init Eureka DBs on built-in PostgresSQL...")
          sh(script: "kubectl exec pod/${pgadmin_pod} -- export PGPASSWORD=${Constants.PG_ROOT_DEFAULT_PASSWORD};/usr/local/pgsql-13/psql -h ${connStr["Servers"]["pg"]["Host"]} -p \"5432\" -u \"postgres\" -a -f /tmp/${db}.sql", returnStdout: true)
        } catch (Exception e) {
          logger.error("Error: ${e.getMessage()}")
        }
      }
    }
  }
}
