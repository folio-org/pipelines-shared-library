#!groovy

@Library('pipelines-shared-library') _

import org.folio.Constants
import org.folio.utilities.Tools
import groovy.json.JsonSlurperClassic
import org.jenkinsci.plugins.workflow.libs.Library

String ui_bundle_repo_name = 'ui-bundle'
def cluster_project_map = new JsonSlurperClassic().parseText(jobsParameters.generateProjectNamesMap())
assert cluster_project_map instanceof Map

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    pipelineTriggers([cron('0 0 * * 6')]),
    parameters([
        jobsParameters.refreshParameters()])
])

List getBackendModulesList(){
    String nameGroup = "moduleName"
    String patternModuleVersion = '^(?<moduleName>.*)-(?<moduleVersion>(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*).*)$'
    def installJson = new URL('https://raw.githubusercontent.com/folio-org/platform-complete/snapshot/install.json').openConnection()
    if (installJson.getResponseCode().equals(200)) {
        List modules_list = ['okapi']
        new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.findAll { it ==~ /mod-.*/ }.each { value ->
            def matcherModule = value =~ patternModuleVersion
            assert matcherModule.matches()
            modules_list.add(matcherModule.group(nameGroup))
        }
        return modules_list.sort()
    }
}

ansiColor('xterm') {
    if (params.refresh_parameters) {
        currentBuild.result = 'ABORTED'
        println('REFRESH CRON PARAMETERS!')
        return
    }
    node('rancher||jenkins-agent-java17') {
        try {
            stage("Cleanup us-west-2 ui-bundle repo") {
                folioHelm.withK8sClient {
                    String image_list = awscli.listEcrImages(Constants.AWS_REGION, ui_bundle_repo_name)
                    cluster_project_map.each {cluster, project ->
                        project.each {value->
                            List images_to_remove = []
                            List images = new Tools(this).findAllRegex(image_list, "${cluster}-${value}\\.(.*?)..*")
                            if (!images.isEmpty()) {
                                images_to_remove.addAll(images.take(images.size() - 2))
                            }
                            images_to_remove.each { image_tag ->
                                awscli.deleteEcrImage(Constants.AWS_REGION, ui_bundle_repo_name, image_tag.toString())
                            }
                        }
                    }
                }
            }
            stage("Cleanup us-west-2 mod-* and okapi repos") {
                def backend_modules_list = getBackendModulesList()
                folioHelm.withK8sClient {
                    backend_modules_list.each { module_repo ->
                        if (!awscli.isEcrRepoExist(Constants.AWS_REGION, module_repo)) {
                            String image_list = awscli.listEcrImages(Constants.AWS_REGION, module_repo.toString())
                            List images = new JsonSlurperClassic().parseText(image_list)
                            List images_to_remove = []
                            if (!images.isEmpty()) {
                                images_to_remove.addAll(images.take(images.size() - 1))
                            }
                            images_to_remove.each { image_tag ->
                                awscli.deleteEcrImage(Constants.AWS_REGION, module_repo.toString(), image_tag.toString())
                            }
                        }
                        else {println("Repository ${module_repo.toString()} doesn't exist in ${Constants.AWS_REGION} region. Skip...")}
                    }
                }
            }
        } catch (exception) {
            println(exception)
            error(exception.getMessage())
        } finally {
            stage('Cleanup') {
                cleanWs notFailBuild: true
            }
        }
    }
}
