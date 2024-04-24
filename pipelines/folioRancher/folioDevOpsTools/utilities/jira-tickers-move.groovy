#!groovy
@Library('pipelines-shared-library') _

import org.folio.client.jira.JiraClient
import org.folio.client.jira.model.JiraIssue
import org.jenkinsci.plugins.workflow.libs.Library

Object list_of_found_jira_tasks
ArrayList list_of_jira_tasks_to_change = []
String search_pattern = ""
LinkedHashMap bugfest_map = [:]
JiraClient jiraClient = JiraClient.getJiraClient(this)
LinkedHashMap host_map = ["Nolana"       :"https://okapi-bugfest-nolana.int.aws.folio.org",
                          "Orchid"       :"https://okapi-bugfest-orchid.int.aws.folio.org",
                          "Pre-Orchid"   :"https://okapi-pre-bugfest-orchid.int.aws.folio.org",
                          "Morning-Glory":"https://okapi-bugfest-mg.int.aws.folio.org"
                    ] as LinkedHashMap
ArrayList<String> Bugfest_envs = ["Orchid", "Nolana", "Pre-Orchid", "Morning-Glory"]
String tenant="fs09000000"

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        jobsParameters.agents(),
        choice(name: 'BugfestEnv', choices: Bugfest_envs, description: 'Choose what Bugfest host you are going to use'),
        string(name: 'Release', defaultValue: 'Nolana (R3 2022)', description: 'Name of current Release', trim: true),
        string(name: 'JiraTaskStatus', defaultValue: 'Awaiting deployment', description: 'Current status of jira task', trim: true),
        string(name: 'NextJiraTaskStatus', defaultValue: 'In bugfix review', description: 'Future status of jira task', trim: true),
        string(name: 'Comment', defaultValue: 'Deployed to the ${BugfestEnv} bf env. Moved status to ${NextJiraTaskStatus} from status ${JiraTaskStatus}. Please proceed with the verification.', description: 'Comment for jira task', trim: true),
    ])
])


ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node(params.agent) {
        try {
            stage('Check input') {
                double_check=0
                if ((Release!=null) && (Release!="")){
                    release_check="release = \"${Release}\""
                    double_check++
                }
                if ((JiraTaskStatus!=null) && (JiraTaskStatus!="")){
                    jira_status_check="status = \"${JiraTaskStatus}\""
                    double_check++
                }
                if (double_check==2){
                    search_pattern="${release_check} and ${jira_status_check}"
                }
                else {
                    error("Error in input data")
                }
            }
            stage('Search Jira tasks') {
                List<JiraIssue> issues = jiraClient.searchIssues(search_pattern,
                    ["key","status","fixVersions","project"])
                list_of_found_jira_tasks=SortJiraTickersByVersion(issues)
            }
            stage('Get map with services from bugfest') {
                String host = host_map[BugfestEnv]
                def response = getRequest(host, tenant)
                def json = new groovy.json.JsonSlurper().parseText(response)

                json.each{ i ->
                    list_key_value="${i}".split(':')
                    value = list_key_value[-1].substring(0, list_key_value[-1].length() - 1)

                    if (value.contains("folio_")){
                        value=value.replace("folio_","ui-")
                    }
                    pattern="-[0-9]"
                    key="${value}".split(pattern)[0]
                    value="${value}".split("${key}-")[-1]

                    bugfest_map.put(key, value)
                }
            }
            stage('Compare versions of modules') {
                for(i in list_of_found_jira_tasks){
                    check_is_less=false
                    try {
                        service_version=bugfest_map[i.project]
                        jira_service_version=i.fixVersions.substring(1, i.fixVersions.length() - 1)
                        if (service_version!=null){
                            check_is_less=false
                            service_version_list=service_version.replace(".","").split(",")
                            jira_service_version_list=jira_service_version.replace(".","").split(",")

                            if (service_version_list.length == jira_service_version_list.length){
                                for (j=0 ; j < jira_service_version_list.size(); j++){
                                    //Compare jira and bugfest versions
                                    if (jira_service_version_list[j].toInteger() <= service_version_list[j].toInteger()){
                                        check_is_less=true
                                        break
                                    }
                                }
                            }
                        }
                        if (check_is_less) {
                            list_of_jira_tasks_to_change.add(i)
                        }
                    }catch (exception){
                        println(exception)
                    }
                }
            }
            stage('Change status of Jira ticket') {
                for(i in list_of_jira_tasks_to_change) {
                    jiraClient.addIssueComment(i.id, Comment)
                    jiraClient.issueTransition(i.id, NextJiraTaskStatus)
                    println "Jira ticket '${i.key}' status changed to '${NextJiraTaskStatus}'"
                    println "Jira ticket version '${i.fixVersions}' bugfest version'${bugfest_map[i.project]}'"
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

private static SortJiraTickersByVersion(List<JiraIssue> list_of_jira_maps){
    ArrayList list_maps_with_empty_params = []
    for(i in list_of_jira_maps){
        if ((i.key==null) || (i.status==null) || (i.fixVersions=="[]") || (!i.fixVersions.contains(".")) || (i.project==null)){
            list_maps_with_empty_params.add(i)
        }
    }
    //Remove all jira tickers from list that has empty fields or none correct version
    for(i in list_maps_with_empty_params){
            list_of_jira_maps.remove(i)
    }
    return list_of_jira_maps as Object
}

private getRequest(String host,String tenant) {
    pipeline = new URL("${host}/_/proxy/tenants/${tenant}/modules")
        .getText(connectTimeout: 5000,
            readTimeout: 10000,
            useCaches: true,
            allowUserInteraction: false,
            requestProperties: ['Connection': 'close'])
    return pipeline
}
