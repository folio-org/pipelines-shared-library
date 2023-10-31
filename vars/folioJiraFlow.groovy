import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.folio.utilities.RestClient
import org.folio.client.jira.JiraClient
/*
***folioRancher general library for processing existing and new entries***
* Functionality:
* CRUD operations for Jira: tickets & comments.
 */

RestClient RestClient = new RestClient(this)
JsonOutput JsonOutput = new JsonOutput()
JsonSlurperClassic JsonSlurperClassic = new JsonSlurperClassic()
