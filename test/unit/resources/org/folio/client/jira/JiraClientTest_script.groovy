package org.folio.version

import org.folio.client.jira.JiraClient

def execute() {
    def client = new JiraClient(this, "https://issues.folio.org", "dummy", "dummy")
    client.createJiraTicket "KRD", "Task", "Test summary",
        [Description       : "Description long",
         Priority          : "P1",
         Labels            : ["reviewed"],
         "Development Team": "Team"]
}
