package org.folio.rest

import groovy.json.JsonOutput
import groovy.text.GStringTemplateEngine
import hudson.AbortException
import org.folio.rest.model.Email
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant

class TenantConfiguration extends GeneralParameters {

  private Authorization auth = new Authorization(steps, okapi_url)

  TenantConfiguration(Object steps, String okapi_url) {
    super(steps, okapi_url)
  }

  void modInventoryMods(OkapiTenant tenant, Boolean large = false) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String filePath
    String statusUrl
    String url = okapi_url + "/inventory/ingest/mods"
    ArrayList headers = [
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    if (large) {
      filePath = tools.copyResourceFileToWorkspace("okapi/large-mods-records.xml")
    } else {
      filePath = tools.copyResourceFileToWorkspace("okapi/multiple-mods-records.xml")
    }
    logger.info("Uploading ${filePath} to mod-inventory")
    Object res = http.uploadRequest(url, filePath, headers)
    if (res.status == HttpURLConnection.HTTP_ACCEPTED) {
      statusUrl = res.headers.Location[0]
      //This if block handles situation when okapi return http link instead of https (at the moment there is no http to https redirect on okapi)
      if (statusUrl.startsWith("http://")) {
        statusUrl = statusUrl.replace("http://", "https://")
      }
      steps.timeout(2) {
        steps.waitUntil(quiet: true) {
          def uploadStatus = http.getRequest(statusUrl, headers)
          if (uploadStatus.status == HttpURLConnection.HTTP_OK) {
            logger.info(tools.jsonParse(uploadStatus.content).status)
            return tools.jsonParse(uploadStatus.content).status == "Completed"
          } else {
            throw new AbortException("Can not get inventory upload status" + http.buildHttpErrorMessage(uploadStatus))
          }
        }
      }
    } else {
      throw new AbortException("${filePath} could not be uploaded" + http.buildHttpErrorMessage(res))
    }
  }

  void ebscoRmapiConfig(OkapiTenant tenant) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/eholdings/kb-credentials/80898dee-449f-44dd-9c8e-37d5eb469b1d"
    ArrayList headers = [
      [name: 'Content-type', value: "application/vnd.api+json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    String body = JsonOutput.toJson([
      data: [
        type      : "kbCredentials",
        attributes: [
          name      : "Knowledge Base",
          apiKey    : tenant.kb_api_key,
          url       : OkapiConstants.EBSCO_API_URL,
          customerId: OkapiConstants.EBSCO_CUSTOMER_ID
        ]

      ]
    ])
    logger.info("Setting rmapi key")
    def res = http.putRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_NO_CONTENT) {
      logger.info("EBSCO rmapi key successfully set")
    } else {
      throw new AbortException("EBSCO rmapi key can not be set" + http.buildHttpErrorMessage(res))
    }
  }

  boolean worldcatCheck(OkapiTenant tenant) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/copycat/profiles/f26df83c-aa25-40b6-876e-96852c3d4fd4"
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      logger.info("Worldcat exists")
      return true
    } else {
      logger.info("Worldcat - copycat/profiles/{id} is not exists")
      return false
    }
  }

  void worldcat(OkapiTenant tenant) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/copycat/profiles/f26df83c-aa25-40b6-876e-96852c3d4fd4"
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    String body = JsonOutput.toJson(OkapiConstants.WORLDCAT)
    if (worldcatCheck(tenant)) {
      def res = http.putRequest(url, body, headers)
      if (res.status == HttpURLConnection.HTTP_NO_CONTENT) {
        logger.info("Worldcat successfully set")
      } else {
        throw new AbortException("Worldcat can not be set" + http.buildHttpErrorMessage(res))
      }
    } else {
      logger.warning("Worldcat's id not exits, reference data not performed")
    }
  }

  void configurations(OkapiTenant tenant, Email email, String stripes_url) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/configurations/entries"
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '']
    ]
    def binding = [
      email_smtp_host: email.smtpHost,
      email_smtp_port: email.smtpPort,
      email_username : email.username,
      email_password : email.password,
      email_from     : email.from,
      stripes_url    : stripes_url
    ]
    OkapiConstants.CONFIGURATIONS.each {
      def content = steps.readFile file: tools.copyResourceFileToWorkspace("okapi/configurations/" + it)
      String body = new GStringTemplateEngine().createTemplate(content).make(binding).toString()
      def res = http.postRequest(url, body, headers)
      if (res.status == HttpURLConnection.HTTP_CREATED) {
        logger.info("Template ${it} succesfully applied")
      } else if (res.status == 422) {
        logger.warning("Configuration already presented" + http.buildHttpErrorMessage(res))
      } else {
        throw new AbortException("Template ${it} can not be applied" + http.buildHttpErrorMessage(res))
      }
    }
  }

}
