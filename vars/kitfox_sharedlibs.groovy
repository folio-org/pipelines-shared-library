#!groovy

import groovy.json.JsonOutput

def call() {}


/**
 * Do a HTTP POST and return the response object
 *
 * @param url
 * @param headers
 * @param body
 * @return
 */
def httpPost(String url, List headers, String body) {
  def response = httpRequest httpMode: 'POST', url: url,
    requestBody: body, contentType: 'APPLICATION_JSON', acceptType: 'APPLICATION_JSON',
    customHeaders: headers, validResponseCodes: '100:599'
  return response
}

/**
 * Login and return x-okapi-token as a String or null
 * @param okapiUrl
 * @param tenant
 * @param username
 * @param password
 * @return
 */
String login(String okapiUrl, String tenant, String username, String password) {
  def url = "${okapiUrl}/authn/login"
  def headers = [
    [name: 'x-okapi-tenant', value: tenant]
  ]
  def body = JsonOutput.toJson([username: username, password: password])
  def response = httpPost(url, headers, body)
  if (response.status == 201) {
    return response.headers['x-okapi-token'][0]
  } else {
    println JsonOutput.toJson(response)
    return null
  }
}

/**
 * Login and return headers that contain either x-okapi-token or x-okapi-tenant
 *
 * @param okapiUrl
 * @param tenant
 * @param username
 * @param password
 * @return
 */
List getOkapiHeader(String okapiUrl, String tenant, String username, String password) {
  def token = login(okapiUrl, tenant, username, password)
  return token ? [
    [name: 'x-okapi-tenant', value: tenant],
    [name: 'x-okapi-token', value: token, maskValue: true]
  ]
    : [
    [name: 'x-okapi-tenant', value: tenant]
  ]
}

/**
 * Utility to recreate indeces in the ElasticSearch and mod-search module
 *
 * @param ctx - should be a map with <code>account</code>, <code>region</code>, <code>folio</code>, <code>tenantId</code>, <code>tenantUrlPrefix</code>
 * In case of ctx is not specified method will use fse.jenkinsParamsToContext method for getting context from build parameters
 *
 * @return nothing
 */
def reindex() {
  def header = getOkapiHeader(okapiUrl, tenant, username, password)
  def reindexResponse = httpRequest httpMode: 'POST', url: "${okapiUrl}/search/index/inventory/reindex", customHeaders: header, contentType: 'APPLICATION_JSON', consoleLogResponseBody: true
  /*
  * this block is commented out due to lack of rigts folio user
  * Access requires permission: inventory-storage.instance.reindex.item.get
  def jobId = readJSON(text: reindexResponse.content)['id']
  timeout(10) {
      waitUntil {
          def resp = httpRequest httpMode: 'GET', url: "${okapiUrl}/instance-storage/reindex/${jobId}", customHeaders: header, contentType: 'APPLICATION_JSON', consoleLogResponseBody: true
          return (readJSON(text: resp.content)['jobStatus'] == 'Ids published')
      }
  }
  */
}

def worldcat() {
  def header = getOkapiHeader(okapiUrl, tenant, username, password)
  def worldcatResponse = httpRequest consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', httpMode: 'PUT', customHeaders: header, ignoreSslErrors: true, requestBody: '{"id":"f26df83c-aa25-40b6-876e-96852c3d4fd4","name":"OCLC WorldCat","url":"zcat.oclc.org/OLUCWorldCat","externalIdQueryMap":"@attr 1=1211 $identifier","internalIdEmbedPath":"999ff$i","createJobProfileId":"d0ebb7b0-2f0f-11eb-adc1-0242ac120002","updateJobProfileId":"91f9b8d6-d80e-4727-9783-73fb53e3c786","targetOptions":{"charset":"utf-8"},"externalIdentifierType":"439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef","enabled":true,"authentication":"100473910/PAOLF"}', responseHandle: 'NONE', url: "${okapiUrl}/copycat/profiles/f26df83c-aa25-40b6-876e-96852c3d4fd4"

}

def eholdings() {
  def header = getOkapiHeader(okapiUrl, tenant, username, password)
  header += [name: "Content-Type", value: "application/vnd.api+json"]
  def body = "{\"data\":{\"id\":\"80898dee-449f-44dd-9c8e-37d5eb469b1d\",\"type\":\"kbCredentials\", \"attributes\":{\"url\":\"https://api.ebsco.io\", \"customerId\":\"apidvcorp\", \"name\":\"Knowledge Base\", \"apiKey\":\"${cypress_api_key_apidvcorp}\"}}}"
  def eholdingsResponse = httpRequest consoleLogResponseBody: true,
    httpMode: 'PUT',
    customHeaders: header,
    ignoreSslErrors: true,
    requestBody: body,
    responseHandle: 'NONE',
    url: "${okapiUrl}/eholdings/kb-credentials/80898dee-449f-44dd-9c8e-37d5eb469b1d"
}
