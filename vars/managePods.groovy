import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.utilities.Logger

def handlePods(String clusterName, String action, String ns, String start_time = '') {
    buildName "${clusterName}"
    folioHelm.withKubeConfig(clusterName) {
        Calendar calendar = Calendar.getInstance()
        Logger logger = new Logger(this, 'managePods')
        List namespaces = sh(script: "kubectl get namespaces -o jsonpath='{.items[*].metadata.name}'", returnStdout: true).trim().tokenize()
        def day_of_week = calendar.get(Calendar.DAY_OF_WEEK)
        def check = day_of_week in [1, 7] ? Constants.RANCHER_KNOWN_NAMESPACES.remove('sprint') : Constants.RANCHER_KNOWN_NAMESPACES
        namespaces.each { namespace ->
            if (namespace.toString().trim() in check) {
                logger.info("Service namespace: ${namespace} bypassed. Skipping pods management.")
            } else {
                logger.info("Running in namespace: ${namespace}")
                switch (action) {
                    case 'stop':
                        def labels = kubectl.getLabelsFromNamespace("${namespace}")
                        def status = new JsonSlurperClassic().parseText(labels)
                        if (status['suspend'] == 'yes' && ns.trim() == namespace.toString().trim()) {
                            kubectl.deleteLabelFromNamespace("${namespace}", "suspend")
                        } else {
                            if (namespace.toString().trim() in Constants.AMERICA_TIME_ZONE_TEAMS && start_time == '19') {
                                logger.info("America time zone namespace: ${namespace} bypassed. Skipping pods management.")
                            } else {
                                def sts = kubectl.getKubernetesStsNames(namespace.toString().trim())
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
                        }
                        break
                    case 'start':
                        buildDescription "Starting pods management for namespace: ${namespace}"
                        if (ns.trim() == namespace.toString().trim()) {
                            def sts = kubectl.getKubernetesStsNames(namespace.toString().trim())
                            if (!sts.contains('postgresql')) {
                                awscli.startRdsCluster("rds-${clusterName}-${namespace}", Constants.AWS_REGION)
                                awscli.waitRdsClusterAvailable("rds-${clusterName}-${namespace}", Constants.AWS_REGION)
                                sleep 30
                            }
                            kubectl.scaleUpResources("${namespace}", "StatefulSet")
                            sleep time: 3, unit: 'MINUTES'
                            kubectl.scaleUpResources("${namespace}", "Deployment")
                            folioPrint.colored("Pods management started for namespace: ${namespace}\nPlease wait for a 5 minutes, before accessing the environment.", "green")
                        }
                        break
                    case 'suspend':
                        if (ns.trim() == namespace.toString().trim()) {
                            kubectl.addLabelToNamespace("${namespace}", "suspend", "yes")
                            folioPrint.colored("Pods management suspended for namespace: ${namespace}\nONLY FOR TONIGHT!", "green")
                        }
                        break
                }
            }
        }
    }
}
