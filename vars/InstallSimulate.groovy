import groovy.json.JsonSlurper

@Library('pipelines-shared-library@RANCHER-741-Jenkins-Enhancements') _
def install_simulate(String clusterName, String tenant, String okapi_url, String input_json){

    folioHelm.withKubeConfig(clusterName)

}
