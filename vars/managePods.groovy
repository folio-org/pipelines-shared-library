import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.utilities.Logger

def handlePods(String clusterName, String action, String ns) {
  buildName "${clusterName}"
  folioHelm.withKubeConfig(clusterName) {
    Logger logger = new Logger(this, 'managePods')
    List namespaces = sh(script: "kubectl get namespaces -o jsonpath='{.items[*].metadata.name}'", returnStdout: true).trim().tokenize()
    namespaces.each { namespace ->
      if (namespace.toString().trim() in Constants.RANCHER_KNOWN_NAMESPACES) {
        logger.info("Service namespace: ${namespace} bypassed. Skipping pods management.")
      } else {
        logger.info("Running in namespace: ${namespace}")
        switch (action) {
          case 'stop':
            def labels = kubectl.getLabelsFromNamespace("${namespace}")
            def status = new JsonSlurperClassic().parseText(labels)
            if (status['suspend'] == 'yes' && namespace == ns) {
              kubectl.deleteLabelFromNamespace("${namespace}", "suspend")
            } else {
              def sts = sh(returnStdout: true, script: "kubectl get sts --namespace ${namespace} -o jsonpath='{.items[*].metadata.name}' --ignore-not-found").trim()
              kubectl.scaleDownResources("${namespace}", "Deployment")
              kubectl.scaleDownResources("${namespace}", "StatefulSet")
              if (!sts.contains('postgresql')) {
                try {
                  awscli.stopRdsCluster("rds-${clusterName}-${namespace}", Constants.AWS_REGION)
                } catch (Exception e) {
                  println(namespace.toString() + " does not have DB to stop\n" + "Error: " + e.getMessage())
                }

              }
              folioPrint.colored("Pods management stopped for namespace: ${namespace}", "green")
            }
            break
          case 'start':
            if (namespace == ns) {
              def sts = sh(returnStdout: true, script: "kubectl get sts --namespace ${namespace} -o jsonpath='{.items[*].metadata.name}' --ignore-not-found").trim()
              if (!sts.contains('postgresql')) {
                awscli.startRdsCluster("rds-${clusterName}-${namespace}", Constants.AWS_REGION)
                awscli.waitRdsClusterAvailable("rds-${clusterName}-${namespace}", Constants.AWS_REGION)
                sleep 30
              }
              kubectl.scaleUpResources("${namespace}", "StatefulSet")
              sleep time: 1, unit: 'MINUTES'
              kubectl.scaleUpResources("${namespace}", "Deployment")
              folioPrint.colored("Pods management started for namespace: ${ns}", "green")
            }
            break
          case 'suspend':
            if (ns.trim() == namespace.toString().trim()) {
              kubectl.addLabelToNamespace("${namespace}", "suspend", "yes")
              folioPrint.colored("Pods management suspended for namespace: ${namespace}\nONLY FOR TO NIGHT", "green")
            }
            break
        }
      }
    }
  }
}
