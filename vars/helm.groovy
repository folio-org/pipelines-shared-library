import org.folio.Constants
import org.folio.utilities.Logger
import org.folio.utilities.model.Project
import java.time.LocalDateTime

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
String generateModuleValues(String module_name, String module_version, Project project_config, String domain = '', Boolean custom_module = false) {
    String values_path = "./values"
    Map config = project_config.getModulesConfig()
    if (config[(module_name)]) {
        String repository
        if (custom_module || module_name == 'ui-bundle') {
            repository = Constants.ECR_FOLIO_REPOSITORY
        } else {
            repository = module_version.contains('SNAPSHOT') ? "folioci" : "folioorg"
        }
        config[(module_name)] << [image: [repository: "${repository}/${module_name}",
                                          tag       : module_version]]
        config[(module_name)] << [podAnnotations: [creationTimestamp: "\"${LocalDateTime.now().withNano(0).toString()}\""]]
        def kube_ingress = config[module_name].containsKey('ingress') ? config[module_name]['ingress']['enabled'] : null
        if (kube_ingress) {
            config[(module_name)]['ingress']['hosts'][0] += [host: domain]
            config[(module_name)]['ingress']['annotations'] += ['alb.ingress.kubernetes.io/group.name': "${project_config.getClusterName()}.${project_config.getProjectName()}"]
        }
        def kube_service = config[(module_name)].containsKey('service') ? config[(module_name)]['service']['type'] : null
        if (kube_service == 'LoadBalancer') {
            def edge_nlb_domain = ''
            switch (module_name) {
                case 'edge-sip2':
                    edge_nlb_domain = common.generateDomain(project_config.getClusterName(), project_config.getProjectName(), 'sip2', Constants.CI_ROOT_DOMAIN)
                    config[(module_name)] << [okapiUrl         : "https://${project_config.getDomains().okapi}",
                                              sip2TenantsConfig: """{
  "scTenants": [
    {
      "scSubnet": "0.0.0.0/0",
      "tenant": "${project_config.tenant.getId()}",
      "errorDetectionEnabled": false,
      "messageDelimiter": "\\r",
      "charset": "ISO-8859-1"
    }
  ]
}"""
                    ]
                    break
                case 'edge-connexion':
                    edge_nlb_domain = common.generateDomain(project_config.getClusterName(), project_config.getProjectName(), 'connexion', Constants.CI_ROOT_DOMAIN)
                    break
                default:
                    new Logger(this, 'helm').warning("Missing nlb configuration for module ${module_name}")
                    break
            }
            config[(module_name)]['service']['annotations'] += ['external-dns.alpha.kubernetes.io/hostname': edge_nlb_domain]
        }
        writeYaml file: "${values_path}/${module_name}.yaml", data: config[module_name]
        return values_path
    } else {
        new Logger(this, 'helm').error("Values for ${module_name} not found!")
    }
}
