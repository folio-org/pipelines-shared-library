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