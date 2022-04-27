package org.folio.client.jira

import jenkins.plugins.http_request.ResponseContentSupplier
import org.folio.testharness.AbstractScriptTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JiraClientTest extends AbstractScriptTest {

    @Test
    void testCreateTicket() {
        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
            def content
            if (parameters["url"].contains("issue/createmeta")) {
                content = getResourceContent("testCreateTicket_issuecreatemeta.json")
            } else if (parameters["url"].contains("editmeta")) {
                content = getResourceContent("testCreateTicket_issueeditmeta.json")
            } else {
                content = getResourceContent("testCreateTicket_response.json")
            }
            return new ResponseContentSupplier(content, 200)
        })

        String result = getClassScript().execute()

        Assertions.assertEquals("57997", result)
    }
}
