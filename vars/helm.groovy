import org.folio.Constants
import org.folio.utilities.Logger

// A function that is used to run the k8sClient inside a docker container.
def k8sClient(Closure body) {
    withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                      credentialsId    : Constants.AWS_CREDENTIALS_ID,
                      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        docker.image(Constants.DOCKER_K8S_CLIENT_IMAGE).inside("-u 0:0 --entrypoint=") {
            body()
        }
    }
}

// Getting the kubeconfig file from the AWS EKS cluster.
String getKubeConfig(String region, String cluster_name) {
    sh "aws eks update-kubeconfig --region ${region} --name ${cluster_name}"
}

// Adding a helm repo.
def addRepo(String repo_name, String repo_url) {
    sh "helm repo add ${repo_name} ${repo_url}"
}

// Installing a helm chart.
def install(String name, String namespace, String values_path, String chart_repo, String chart_name) {
    sh "helm install ${name} --namespace=${namespace} -f ${values_path} ${chart_repo}/${chart_name}"
}

// Upgrading the helm chart.
def upgrade(String name, String namespace, String values_path, String chart_repo, String chart_name) {
    sh "helm upgrade --install ${name} --namespace=${namespace} -f ${values_path} ${chart_repo}/${chart_name}"
}

// Deleting the helm chart.
def delete(String name, String namespace) {
    sh "helm delete ${name} --namespace=${namespace}"
}

def createSecret(String name, String namespace, String file_path) {
    try {
        sh "kubectl create secret generic ${name} --namespace=${namespace} --from-file=${file_path}"
    } catch (Exception e) {
        println(e.getMessage())
    }
}

def getS3ObjectBody(String bucketname, String filePathName) {
    sh(script: "aws s3 cp s3://${bucketname}/${filePathName} ./${filePathName} > /dev/null && cat ${filePathName}", returnStdout: true)
}

// Adding the image repository and tag to the module's values.yaml file.
String generateModuleValues(def config, String module_name, String module_version, String cluster_name, String project_name, String hostname = '') {
    String values_path = "./values"
    if (config[(module_name)]) {
        if (module_name == 'ui-bundle') {
            config[(module_name)] << [image: [tag: module_version]]
        } else {
            String repository = module_version.contains('SNAPSHOT') ? "folioci" : "folioorg"
            config[(module_name)] << [image: [repository: "${repository}/${module_name}",
                                              tag       : module_version]]
        }
        def kube_ingress = config[module_name].containsKey('ingress') ? config[module_name]['ingress']['enabled'] : null
        if (kube_ingress) {
            config[(module_name)]['ingress']['hosts'][0] += [host: hostname]
            config[(module_name)]['ingress']['annotations'] += ['alb.ingress.kubernetes.io/group.name': "${cluster_name}.${project_name}"]
        }
        def kube_service = config[(module_name)].containsKey('service') ? config[(module_name)]['service']['type'] : null
        if (kube_service == 'LoadBalancer') {
            def edge_nlb_domain = ''
            switch (module_name) {
                case 'edge-sip2':
                    edge_nlb_domain = common.generateDomain(cluster_name, project_name, 'sip2', Constants.CI_ROOT_DOMAIN)
                    break
                case 'edge-connexion':
                    edge_nlb_domain = common.generateDomain(cluster_name, project_name, 'connexion', Constants.CI_ROOT_DOMAIN)
                    break
                default:
                    new Logger(this, 'helm').warning("Missing nlb configuration for module ${module_name}")
                    break
            }
            config[(module_name)]['service']['annotations'] += ['external-dns.alpha.kubernetes.io/hostname:': "${edge_nlb_domain}"]
        }
        writeYaml file: "${values_path}/${module_name}.yaml", data: config[module_name]
        return values_path
    } else {
        new Logger(this, 'helm').error("Values for ${module_name} not found!")
    }
}
