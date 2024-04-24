package org.folio.version

import org.folio.client.jira.JiraClient
import org.folio.client.jira.JiraConstants

def execute() {
    def client = new JiraClient(this, JiraConstants.URL, "dummy", "dummy")

    client.searchIssues("labels = karateRegressionPipeline", ["summary", "status"])
}
