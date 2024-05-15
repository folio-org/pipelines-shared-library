#!groovy
import groovy.json.JsonSlurperClassic
import java.net.URL
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

def restClient = new RESTClient('https://api.example.com')
def response = restClient.get(path: '/endpoint', contentType: ContentType.JSON)


def CLUSTER_URL = 'folio-tmp'
def NAMESPACE_URL = 'test'
def TENANT_URL = 'diku'
def inputString = "mod-search, mod-circulation, mod-dcb"

def test = []
def values = []
def tmp_values = inputString.split(",").each { it -> test.add(it.trim()) }
def url = new URL("https://${CLUSTER_URL}-${NAMESPACE_URL}-okapi.ci.folio.org/_/proxy/tenants/${TENANT_URL}/modules")
println(url)

try {
    def connection = url.openConnection()
    connection.setRequestProperty('Accept', 'application/json')

    def inputStream = connection.getInputStream()
    def jsonText = inputStream.getText()
    inputStream.close()
    def json = new JsonSlurperClassic().parseText(jsonText)
    test.each { module ->
        try {
            if ( json.find { it.id =~ module }) {
                def tmp = json.find { it.id =~ module }
                tmp['action']='disable'
                values.add(tmp)
            }
        } catch (Exception e) {
            println(e.getMessage())
        }
    }
    println(values)
} catch (Exception e) {
    println "Error: ${e.message}"
}
