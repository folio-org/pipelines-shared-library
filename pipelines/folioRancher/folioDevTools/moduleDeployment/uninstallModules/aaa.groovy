#!groovy
import groovy.json.JsonSlurperClassic
import java.net.URL

def CLUSTER_URL = 'folio-tmp'
def NAMESPACE_URL = 'test'
def TENANT_URL = 'diku'

def inputString = "mod-search, mod-calendar, mod-bulk-operations"
def moduleList = inputString.split(',').collect { "\"${it.trim()}\"" }

println(moduleList)

def test = []
def url = new URL("https://${CLUSTER_URL}-${NAMESPACE_URL}-okapi.ci.folio.org/_/proxy/tenants/${TENANT_URL}/modules")

println(url)

try {
    def connection = url.openConnection()
    connection.setRequestProperty('Accept', 'application/json')

    def inputStream = connection.getInputStream()
    def jsonText = inputStream.getText()
    inputStream.close()

    def json = new JsonSlurperClassic().parseText(jsonText)

    // println(json)

    // List modules = moduleList
    List modules = ["mod-search", "mod-calendar", "mod-bulk-operations"]
    modules.each { module ->
        test +=  [id: json.find {it -> it =~ "${module}"}['id'], action: 'disable']
    }

    println(test)
} catch (Exception e) {
    println "Error: ${e.message}"
}
