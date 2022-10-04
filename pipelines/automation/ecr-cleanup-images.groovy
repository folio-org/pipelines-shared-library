#!groovy

@Library('pipelines-shared-library@RANCHER-466') _

import org.folio.Constants
import org.folio.utilities.Tools
import groovy.json.JsonSlurperClassic
import org.jenkinsci.plugins.workflow.libs.Library

String ui_bundle_repo_name = 'ui-bundle'

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    //pipelineTriggers([cron('*/6 * * * *')])
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
    node(params.agent) {
        try {
            stage('Checkout') {
                checkout scm
            }
            stage("Cleanup us-west-2 ui-bundle repo") {
                helm.k8sClient {
                    String image_list = awscli.listEcrImages(Constants.AWS_REGION, ui_bundle_repo_name)
                    List images_to_remove = []
                    jobsParameters.clustersList().each { cluster ->
                        jobsParameters.devEnvironmentsList().each { project ->
                            List images = new Tools(this).findAllRegex(image_list, "${cluster}-${project}-.*")
                            if (!images.isEmpty()) {
                                images_to_remove.addAll(images.take(images.size() - 1))
                            }
                        }
                    }
                    images_to_remove.each { image_tag ->
                        //awscli.deleteEcrImage(Constants.AWS_REGION, ui_bundle_repo_name, image_tag.toString())
                        println("Delete ${image_tag.toString()} image")
                    }
                }
            }

            stage("Cleanup us-west-2 mod-* and okapi repos") {
                def backend_modules_list = getBackendModulesList()
                helm.k8sClient {
                    backend_modules_list.each { module_repo ->
                        if (!awscli.isEcrRepoExist(Constants.AWS_REGION, module_repo)) {
                            String image_list = awscli.listEcrImages(Constants.AWS_REGION, module_repo.toString())
                            List images = new JsonSlurperClassic().parseText(image_list)
                            List images_to_remove = []
                            if (!images.isEmpty()) {
                                images_to_remove.addAll(images.take(images.size() - 1))
                            }
                            images_to_remove.each { image_tag ->
                                //awscli.deleteEcrImage(Constants.AWS_REGION, module_repo.toString(), image_tag.toString())
                                println("Delete ${image_tag.toString()} image")
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
