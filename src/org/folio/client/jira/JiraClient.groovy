package org.folio.client.jira


import groovy.json.JsonOutput
import hudson.AbortException
import org.apache.http.HttpHeaders
import org.folio.client.jira.model.JiraPriority
import org.folio.client.jira.model.JiraProject
import org.folio.client.jira.model.JiraResources
import org.folio.client.jira.model.JiraStatus

import java.util.logging.Logger

class JiraClient {

    private static final Logger log = Logger.getLogger(JiraClient.class.getName())

    def pipeline

    String url

    String authToken

    JiraParser parser = new JiraParser()

    def createIgnoreFields = ["Priority"]

    Map<String, String> jiraFields

    JiraClient(def pipeline, String url, String user, String password) {
        this.pipeline = pipeline
        this.url = url
        this.authToken = Base64.getEncoder().encodeToString("${user}:${password}".getBytes())
    }

    String createJiraTicket(String projectKey, String issueTypeName, String summary, Map fields) {
        readFields()

        def project = getJiraProject(projectKey)
        if (!project) {
            pipeline.error("Jira project '${projectKey} not found')")
        }

        def issueType = project.issueTypes.find { it.name == issueTypeName }
        if (!issueType) {
            pipeline.error("Jira issue type '${issueTypeName} not found')")
        }

        def preparedFields = [:]
        fields.each { name, value ->
            if (!createIgnoreFields.contains(name)) {
                if (!jiraFields[name]) {
                    log.info("Field ${name} not found in defined jira fields list")
                } else {
                    preparedFields[jiraFields[name]] = value
                }
            }
        }

        def content = """
        {
          "fields": {
            "project": {
              "id": "${project.id}"
            },
            "summary": "${summary}",
            ${
            if (fields.Priority) {
                """"priority": {
                    "id": "${getJiraPriority(fields.Priority).id}"
                },"""
            }
        }
            "issuetype": {
              "id": "${issueType.id}"
            }
            ${
            if (fields) {
                def json = JsonOutput.toJson(preparedFields)
                ",\n" + json.substring(1, json.length() - 1)
            }
        }
          }
        }
"""

        def response = postRequest(JiraResources.ISSUE, content)
        if (response.status < 300) {
            def body = pipeline.readJSON text: response.content
            body.id
        } else {
            throw new AbortException("Unable to create jira ticket. Server retuned ${response.status} status code. Content: ${response.content}")
        }
    }

    JiraProject getJiraProject(String key) {
        withResponse("${JiraResources.PROJECT}/${key}",
            { response, body ->
                parser.parseProject(body)
            },
            "Unable to get jira project with '${key}' key"
        )
    }

    JiraPriority getJiraPriority(String name) {
        withResponse("${JiraResources.PRIORITY}",
            { response, body ->
                def priority = body.find { priority -> priority.name == name }
                parser.parseJiraPriority(priority)
            },
            "Unable to get jira priority with '${name}' name"
        )
    }

    JiraStatus getJiraStatus(String name) {
        withResponse("${JiraResources.STATUS}/${name}",
            { response, body ->
                parser.parseJiraStatus(body)
            },
            "Unable to get jira priority with '${name}' name"
        )
    }

    def readFields() {
        if (!jiraFields) {
            withResponse("${JiraResources.FIELD}",
                { response, body ->
                    jiraFields = [:]
                    body.each { field ->
                        jiraFields[field.name] = field.id
                    }
                },
                "Unable to get jira fields"
            )
        }
    }

    private withResponse(endpoint, successClosure, errorMessage) {
        def response = getRequest(endpoint)

        if (response.status < 300) {
            def body = pipeline.readJSON text: response.content
            return successClosure(response, body)
        } else {
            throw new AbortException("${errorMessage}. Server retuned ${response.status} status code. Content: ${response.content}")
        }
    }


    private getRequest(String endpoint) {
        pipeline.httpRequest url: "${url}/rest/api/2/${endpoint}",
            contentType: "APPLICATION_JSON",
            customHeaders: [[name: HttpHeaders.AUTHORIZATION, value: "Basic ${authToken}"]],
            validResponseCodes: "100:599"
    }

    private postRequest(String endpoint, String contents) {
        pipeline.httpRequest url: "${url}/rest/api/2/${endpoint}",
            httpMode: "POST",
            contentType: "APPLICATION_JSON",
            customHeaders: [[name: HttpHeaders.AUTHORIZATION, value: "Basic ${authToken}"]],
            requestBody: contents,
            validResponseCodes: "100:599"
    }

}
