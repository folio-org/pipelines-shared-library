import org.folio.Constants
import org.folio.models.module.FolioModule
import org.folio.utilities.Logger

def createConfigMap(String name, String namespace, files) {
  try {
    def fromFileArgs = []
    switch (files) {
      case GString:
      case String:
        fromFileArgs.add("--from-file=${files}")
        break
      case List:
        fromFileArgs = files.collect { "--from-file=${it}" }
        break
      default:
        throw new IllegalArgumentException("Unsupported argument type 'files'")
    }
    sh "kubectl create configmap ${name} --namespace=${namespace} ${fromFileArgs.join(' ')} --save-config"
  } catch (Exception e) {
    println(e.getMessage())
  }
}

def deleteConfigMap(String name, String namespace) {
  try {
    sh "kubectl delete configmap ${name} --namespace=${namespace} --ignore-not-found=true"
  } catch (Exception e) {
    println(e.getMessage())
  }
}

def recreateConfigMap(String name, String namespace, String file_path) {
  try {
    sh "kubectl create configmap ${name} --namespace=${namespace} --from-file=${file_path} -o yaml --dry-run | kubectl apply -f -"
  } catch (Exception e) {
    println(e.getMessage())
  }
}

static void rolloutDeployment(String name, String namespace) {
  try {
    sh "kubectl rollout restart deployment ${name} --namespace=${namespace}"
  } catch (Exception e) {
    println(e.getMessage())
  }
}

def getConfigMap(String name, String namespace, String data) {
  try {
    return sh(script: "kubectl get configmap ${name} --namespace=${namespace} -o jsonpath='{.data.${data}}'", returnStdout: true)
  } catch (Exception e) {
    println(e.getMessage())
  }
}

def checkDeploymentStatus(String name, String namespace, String timeout_seconds) {
  try {
    sh "kubectl wait deploy/${name} --namespace=${namespace} --for condition=available --timeout=${timeout_seconds}s"
  } catch (Exception e) {
    println("Deployment ${name} not ready!")
    println(e.getMessage())
  }
}

boolean isDeploymentExist(String name, String namespace) {
  String output = sh(script: "kubectl get deployment ${name} --namespace ${namespace} -o json --ignore-not-found", returnStdout: true).trim()

  if (!output) {
    return false
  }

  Map deployment = readJSON(text: output)

  return !deployment.isEmpty()
}

String getDeploymentImageTag(String name, String namespace) {
  String version = sh(
    script: "kubectl get deployment ${name} --namespace ${namespace} -o jsonpath=\"{.spec.template.spec.containers[0].image}\" | awk -F/ '{print \$2}'",
    returnStdout: true).trim()

  return version
}

String getSecretValue(String namespace, String secret_name, String key_name) {
  try {
    return sh(script: "set +x && kubectl get secret --namespace=${namespace} ${secret_name} -o jsonpath='{.data.${key_name}}' | base64 -d",
      returnStdout: true).trim()
  } catch (Exception e) {
    currentBuild.result = 'UNSTABLE'
    println(e.getMessage())
  }
}

String createSecretWithJson(String secret_name, String json_value, String key_name, String namespace) {
  sh(script: "set +x && kubectl create secret generic ${secret_name} --from-literal='${key_name}'='${json_value}' --namespace=${namespace}")
}

String createSecret(String secret_name, String key_name, String key_name_value, String value_name, String secret_value, String namespace) {
  sh(script: "set +x && kubectl create secret generic ${secret_name} --from-literal='${key_name}'='${key_name_value}' --from-literal='${value_name}'='${secret_value}' --namespace=${namespace}")
}

String deleteSecret(String secret_name, String namespace) {
  return sh(script: "set +x && kubectl delete secret ${secret_name} --namespace=${namespace}", returnStdout: true)
}

String patchSecret(String secret_name, String value_name, String secret_value, String namespace) {
  sh(script: "set +x && kubectl patch secret ${secret_name} --patch='{\"stringData\": { \"${value_name}\": \"${secret_value}\" }}' --namespace=${namespace}")
}

String getDeploymentContainerImage(String namespace, String deploymentName, String containerName) {
  try {
    return sh(script: "kubectl get deployment ${deploymentName} --namespace=${namespace} -o jsonpath='{.spec.template.spec.containers[?(@.name==\"${containerName}\")].image}'", returnStdout: true).trim()
  } catch (Exception e) {
    println("Error retrieving container image: ${e.getMessage()}")
    throw e
  }
}

String getDeploymentContainerImageName(String namespace, String deploymentName, String containerName) {
  String fullPath = getDeploymentContainerImage(namespace, deploymentName, containerName)

  return fullPath.substring(fullPath.lastIndexOf('/') + 1)
}

def patchConfigMap(String name, String namespace, files) {
  try {
    def fromFileArgs = []
    switch (files) {
      case GString:
      case String:
        fromFileArgs.add("--from-file=${files}")
        break
      case List:
        fromFileArgs = files.collect { "--from-file=${it}" }
        break
      default:
        throw new IllegalArgumentException("Unsupported argument type 'files'")
    }
    sh "kubectl create configmap ${name} --namespace=${namespace} ${fromFileArgs.join(' ')} -o json --dry-run=client | kubectl apply -f -"
  } catch (Exception e) {
    println(e.getMessage())
  }
}

void runPodWithCommand(String namespace = 'default', String pod_name, String pod_image, String command = 'sleep 15m') {
  try {
    sh "kubectl run --namespace=${namespace} ${pod_name} --image=${pod_image} --command -- ${command}"
  } catch (Exception e) {
    currentBuild.result = 'UNSTABLE'
    println(e.getMessage())
  }
}

void execCommand(String namespace = 'default', String pod_name, String command) {
  try {
    return sh(script: "set +x && kubectl exec --namespace=${namespace} ${pod_name} -- ${command}",
      returnStdout: true).trim()
  } catch (Exception e) {
//    currentBuild.result = 'UNSTABLE'
    println("Requested command: ${command} failed\nError: " + e.getMessage())
  }
}

void cleanUpFedLocks(String namespace = 'default', int timer = 0, String moduleId = 'mod-agreements', String tenantId = 'default') {
  if (tenantId != 'default') { // condition for future use. DO NOT REMOVE!!!
    println("Trying to cleanup ${tenantId}_${moduleId}.tenant_changelog_lock.")
    String pod = sh(script: "kubectl get pod -l 'app.kubernetes.io/name=pgadmin4' -o=name  --ignore-not-found=true --namespace ${namespace}", returnStdout: true).trim()
    def check = sh(script: "kubectl logs -l 'app.kubernetes.io/name=$moduleId' -c $moduleId --namespace ${namespace}", returnStdout: true)
    check.contains(tenantId) ?
      sh(script: "kubectl exec --request-timeout=10s --namespace=${namespace} ${pod} -- /usr/local/pgsql-16/psql -c 'TRUNCATE ${tenantId}_${moduleId.replace('-', '_')}.tenant_changelog_lock'", returnStatus: false) :
      println("Entitling for tenant: ${tenantId} is not yet completed.")
  } else {
    try {
      switch (timer) {
        case 0:
          println("First check skipped.")
          break
        case 600:
          println("10 minutes passed. Trying to cleanup federation_lock table.")
          String pod = sh(script: "kubectl get pod -l 'app.kubernetes.io/name=pgadmin4' -o=name  --ignore-not-found=true --namespace ${namespace}", returnStdout: true).trim()
          try {
            sh(script: "kubectl exec --request-timeout=10s --namespace=${namespace} ${pod} -- /usr/bin/timeout 30s /usr/local/pgsql-16/psql -c 'TRUNCATE ${moduleId.replace('-', '_')}__system.federation_lock'", returnStatus: false)
          } catch (Exception e) {
            sh(script: "kubectl rollout restart deployment ${moduleId} --namespace=${namespace}", returnStatus: false)
            println("Unable to cleanup federation_lock table.\nError: " + e.getMessage())
          }
          break
        case 1200:
          println("20 minutes passed. Trying to delete $moduleId pod(s) and cleanup federation_lock table.")
          sh(script: "kubectl delete pod -l 'app.kubernetes.io/name=$moduleId' --force --namespace ${namespace}", returnStatus: false)
          String pod = sh(script: "kubectl get pod -l 'app.kubernetes.io/name=pgadmin4' -o=name  --ignore-not-found=true --namespace ${namespace}", returnStdout: true).trim()
          sh(script: "kubectl exec --request-timeout=10s --namespace=${namespace} ${pod} -- /usr/bin/timeout 30s /usr/local/pgsql-16/psql -c 'TRUNCATE ${moduleId.replace('-', '_')}__system.federation_lock'", returnStatus: false)
          break
        default:
          println("Did not reach the expected time yet.")
      }
    } catch (Exception e) {
      println(e.getMessage())
    }
  }
}

void ermEntitlementFix(String namespace = 'default', String tenantId = 'default', String clusterName = '', String moduleName = 'mod-agreements') {
  try {
    folioHelm.withK8sClient {
      awscli.getKubeConfig(Constants.AWS_REGION, clusterName)
      String pod = sh(script: "kubectl get pod -l 'app.kubernetes.io/name=pgadmin4' -o=name  --ignore-not-found=true --namespace ${namespace}", returnStdout: true).trim()
      if (pod) {
        def moduleId = moduleName.replace('-', '_')
        sh(script: "kubectl rollout restart deployment ${moduleName} --namespace=${namespace}", returnStatus: false)
        sh(script: "kubectl exec --request-timeout=10s --namespace=${namespace} ${pod} -- /usr/local/pgsql-16/psql -c 'DROP SCHEMA IF EXISTS ${moduleId}__system CASCADE'", returnStatus: false)
        sh(script: "kubectl exec --request-timeout=10s --namespace=${namespace} ${pod} -- /usr/local/pgsql-16/psql -c 'TRUNCATE ${tenantId}_${moduleId}.tenant_changelog_lock'", returnStatus: false)
        folioHelm.checkDeploymentsRunning(namespace, [moduleName])
      } else {
        println("No pgadmin4 pod found in namespace ${namespace}.")
      }
    }
  } catch (Exception e) {
    println("Error during agreements entitlement fix: " + e.getMessage())
  }
}

void updateApplicationsFix (String namespace = 'default', String clusterName = '', List appIds = [], List tenantIds = []) {
  try {
    folioHelm.withK8sClient {
      awscli.getKubeConfig(Constants.AWS_REGION, clusterName)
      String pod = sh(script: "kubectl get pod -l 'app.kubernetes.io/name=pgadmin4' -o=name  --ignore-not-found=true --namespace ${namespace}", returnStdout: true).trim()
      if (pod) {
        tenantIds.each { tenantId ->
          appIds.each { appId ->
            sh(script: "kubectl exec --request-timeout=10s --namespace=${namespace} ${pod} -- /usr/local/pgsql-16/psql -c \"DELETE FROM public.entitlement WHERE application_id = '${appId}' AND tenant_id = '${tenantId}'\"", returnStatus: false)
          }
        }
      } else {
        println("No pgadmin4 pod found in namespace ${namespace}.")
      }
    }
  } catch (Exception e) {
    println("Error during update applications fix: " + e.getMessage())
  }
}

void deletePod(String namespace = 'default', String pod_name, Boolean wait = true) {
  try {
    sh "kubectl delete pod --namespace=${namespace} ${pod_name} --ignore-not-found=true --wait=${wait} --force --grace-period=0"
  } catch (Exception e) {
    println(e.getMessage())
  }
}

void deleteEvictedPods(String namespace = 'default') {
  try {
    def pods = sh(script: "kubectl get pods --field-selector=status.phase=Failed --no-headers --namespace ${namespace} | grep Evicted | awk '{print \$1}'", returnStdout: true).trim()
    if (pods) {
      pods.tokenize().each { pod ->
        sh(script: "kubectl delete pod ${pod} --force --namespace=${namespace}", returnStatus: false)
      }
    } else {
      println("No evicted pods found. Nothing to delete.")
    }
  } catch (Exception e) {
    println("Unable to delete evicted pods!\nError: " + e.getMessage())
  }
}

void waitPodIsRunning(String namespace = 'default', String pod_name) {
  timeout(5) {
    waitUntil(quiet: true) {
      def status = sh(script: "kubectl get pods --namespace=${namespace} ${pod_name} -o jsonpath='{.status.phase}'",
        returnStdout: true).trim()
      return status == 'Running'
    }
    println("Pod ${pod_name} is now running.")
  }
}

def waitKubernetesResourceStableState(String resource_type, String resource_name, String namespace, String replica_count, String max_wait_time) {
  return sh(script: "start_time=\$(date +%s); while [[ \$(kubectl get ${resource_type} ${resource_name} -n ${namespace} -o=jsonpath='{.status.availableReplicas}') -ne ${replica_count} ]]; do current_time=\$(date +%s); if [[ \$((current_time - start_time)) -gt ${max_wait_time} ]]; then echo \"Deployment did not become stable within ${max_wait_time} seconds.\"; exit 1; fi; sleep 20s; done\n")
}

def getKubernetesResourceList(String resource_type, String namespace) {
  return sh(script: "kubectl get ${resource_type} -n ${namespace} | awk '{if(NR>1)print \$1}'", returnStdout: true).split("\\s+")
}

def getKubernetesResourceCount(String resource_type, String resource_name, String namespace) {
  return sh(script: "kubectl get ${resource_type} ${resource_name} -n ${namespace} -o=jsonpath='{.spec.replicas}'", returnStdout: true)
}

def getKubernetesStsNames(String namespace) {
  return sh(returnStdout: true, script: "kubectl get sts --namespace ${namespace} -o jsonpath='{.items[*].metadata.name}' --ignore-not-found").trim()
}

void setKubernetesResourceCount(String resource_type, String resource_name, String namespace, String replica_count) {
  sh(script: "kubectl scale ${resource_type} ${resource_name} -n ${namespace} --replicas=${replica_count}")
}

boolean checkKubernetesResourceExist(String resource_type, String resource_name, String namespace) {
  return sh(script: "kubectl get ${resource_type} ${resource_name} -n ${namespace}", returnStatus: true)
}

def getLabelsFromNamespace(String namespace, String labelKey = null) {
  try {
    return sh(script: "kubectl get namespace ${namespace} -o jsonpath='{.metadata.labels${labelKey ? '.' + labelKey : ''}}'", returnStdout: true).trim()
  } catch (Exception e) {
    println(e.getMessage())
  }
}

def addLabelToNamespace(String namespace, String labelKey, String labelValue) {
  try {
    sh(script: "kubectl label namespace ${namespace} ${labelKey}=${labelValue} --overwrite=true")
  } catch (Exception e) {
    println(e.getMessage())
  }
}

def deleteLabelFromNamespace(String namespace, String labelKey) {
  try {
    sh(script: "kubectl label namespace ${namespace} ${labelKey}-")
  } catch (Exception e) {
    println(e.getMessage())
  }
}

def collectDeploymentState(String namespace) {
  String jsonPath = '-o jsonpath=\'{range .items[?(@.kind=="Deployment")]}"{.metadata.name}"{":"}"{.status.replicas}"{","}{end}\''
  try {
    return sh(script: "kubectl get all ${jsonPath} --namespace ${namespace}", returnStdout: true)
  }
  catch (Exception e) {
    println(e.getMessage())
  }
}

def scaleDownResources(String namespace, String resource_type) {
  try {
    return sh(script: "kubectl scale ${resource_type} --replicas=0 --all --namespace ${namespace}", returnStdout: true)
  }
  catch (Exception e) {
    println(e.getMessage())
  }
}

def scaleUpResources(String namespace, String resource_type) {
  try {
    return sh(script: "kubectl scale ${resource_type} --replicas=1 --all --namespace ${namespace}", returnStdout: true)
  }
  catch (Exception e) {
    println(e.getMessage())
  }
}

boolean checkNamespaceExistence(String namespace) {
  try {
    String result = sh(script: "kubectl get namespace ${namespace} -o jsonpath='{.metadata.name}'", returnStdout: true).trim()
    return result == namespace
  }
  catch (Exception e) {
    println(e.getMessage())
    return false
  }
}

void portForwardPSQL(String namespace, Map ports = [5432: 5432]) {
  try {
    sh(script: "kubectl pod/port-forward postgresql-${namespace}-0 ${ports} -n ${namespace}")
  } catch (Exception e) {
    new Logger(this, 'kubectl').error("Unable to forward port,\nError: ${e.getMessage()}")
  }
}

void patchDefaultServiceAccount(String namespace) {
  try {
    withCredentials([usernamePassword(credentialsId: 'DockerHubIDJenkins', passwordVariable: 'dockerPassword', usernameVariable: 'dockerUser')]) {
      sh(script: """kubectl create secret docker-registry docker-hub --docker-server=https://index.docker.io/v1/ --docker-username=${env.dockerUser} --docker-password=${env.dockerPassword} --docker-email=${Constants.EMAIL_FROM} ---namespace ${namespace}""")
      sh(script: """kubectl patch sa default -p '{"imagePullSecrets":[{"name": "docker-hub"}]} --namespace ${namespace}""")
    }
  } catch (Exception e) {
    new Logger(this, 'kubectl').error("Unable to patch default service account,\nError: ${e.getMessage()}")
  }
}

def checkNamespaceStatus(String namespaceName) {
  try {
    def status = sh(script: "kubectl get pods --namespace ${namespaceName}", returnStdout: true).trim()
    return status
  } catch (Exception e) {
    println(e.getMessage())
    return false
  }
}
