def createConfigMap(String name, String namespace, String file_path) {
    try {
        sh "kubectl create configmap ${name} --namespace=${namespace} --from-file=${file_path}"
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

def rolloutDeployment(String name, String namespace) {
    try {
        sh "kubectl rollout restart deployment ${name} --namespace=${namespace}"
    } catch (Exception e) {
        println(e.getMessage())
    }
}

def getConfigMap(String name, String namespace, String data) {
    try {
        sh(script: "kubectl get configmap ${name} --namespace=${namespace} -o jsonpath='{.data.${data}}'", returnStdout: true)
    } catch (Exception e) {
        println(e.getMessage())
    }
}

def checkDeploymentStatus(String name, String namespace) {
    try {
        sh "kubectl wait deploy/${name} --namespace=${namespace} --for condition=available --timeout=10s"
    } catch (Exception e) {
        println("Deployment ${name} not ready!")
        println(e.getMessage())
    }
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
        currentBuild.result = 'UNSTABLE'
        println(e.getMessage())
    }
}

void deletePod(String namespace = 'default', String pod_name) {
    try {
        sh "kubectl delete pod --namespace=${namespace} ${pod_name}"
    } catch (Exception e) {
        currentBuild.result = 'UNSTABLE'
        println(e.getMessage())
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
