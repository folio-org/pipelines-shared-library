import org.folio.Constants
import org.folio.utilities.Logger
import org.folio.utilities.model.Project
import java.time.LocalDateTime

// A function that is used to run the k8sClient inside a docker container.
/*
Deprecated. Use folioHelm.withK8sClient()
 */
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

/*
Deprecated. Use folioHelm.addHelmRepository()
 */
// Adding a helm repo.
def addRepo(String repo_name, String repo_url, Boolean use_Nexus_creds = false) {
    if (use_Nexus_creds) {
        withCredentials([usernamePassword(credentialsId: Constants.NEXUS_PUBLISH_CREDENTIALS_ID, usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
            sh "helm repo add ${repo_name} ${repo_url} --username ${NEXUS_USERNAME} --password ${NEXUS_PASSWORD}"
        }
    } else {
        sh "helm repo add ${repo_name} ${repo_url}"
    }
}

/*
Deprecated. Use folioHelm.install()
 */
// Installing a helm chart.
def install(String name, String namespace, String values_path, String chart_repo, String chart_name) {
    sh "helm install ${name} --namespace=${namespace} -f ${values_path} ${chart_repo}/${chart_name}"
}

/*
Deprecated. Use folioHelm.upgrade()
 */
// Upgrading the helm chart.
def upgrade(String name, String namespace, String values_path, String chart_repo, String chart_name) {
    sh "helm upgrade --install ${name} --namespace=${namespace} -f ${values_path} ${chart_repo}/${chart_name}"
}

// Deleting the helm chart.
def delete(String name, String namespace) {
    sh "helm delete ${name} --namespace=${namespace}"
}

def getS3ObjectBody(String bucketname, String filePathName) {
    sh(script: "aws s3 cp s3://${bucketname}/${filePathName} ./${filePathName} > /dev/null && cat ${filePathName}", returnStdout: true)
}

// Adding the image repository and tag to the module's values.yaml file.
String generateModuleValues(String module_name, String module_version, Project project_config, String domain = '', Boolean custom_module = false, Boolean enable_rw_split = false) {
    String values_path = "./values"
    Map config = project_config.getModulesConfig()
    if (config[(module_name)]) {
        String repository
        if (custom_module || module_name == 'ui-bundle') {
            repository = Constants.ECR_FOLIO_REPOSITORY
        } else if (module_name == 'mod-graphql' && module_version ==~ /\d{1,2}.\d{1,3}.\d{3,10}/) {
            repository = "folioci"
        } else {
            repository = module_version.contains('SNAPSHOT') ? "folioci" : "folioorg"
        }
        config[(module_name)] << [image: [repository: "${repository}/${module_name}",
                                          tag       : module_version]]
        config[(module_name)] << [podAnnotations: [creationTimestamp: "\"${LocalDateTime.now().withNano(0).toString()}\""]]

        // Enable JMX metrics
        if (Constants.JMX_METRICS_AVAILABLE[module_name]) {
            def action = compare.compareVersion(Constants.JMX_METRICS_AVAILABLE[module_name], module_version)
            if (action == "upgrade" || action == "equal") {
                config[(module_name)]['javaOptions'] += " -javaagent:./jmx_exporter/jmx_prometheus_javaagent-0.17.2.jar=9991:./jmx_exporter/prometheus-jmx-config.yaml"
            }
        }

        // Enable R/W split
        if (enable_rw_split && Constants.READ_WRITE_MODULES.contains(module_name)) {
            config[(module_name)] << [readWriteSplitEnabled: "true"]
        }

        //Enable consortia env variable

        if (Constants.CONSORTIUM_ENABLED.contains(module_name)){
            config[(module_name)] << [consortiumEnabled: "true"]
        }

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
