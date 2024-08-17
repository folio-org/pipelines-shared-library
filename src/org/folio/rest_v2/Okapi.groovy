package org.folio.rest_v2

import groovy.json.JsonOutput
import org.folio.models.Index
import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException

import java.time.Duration
import java.time.Instant
import java.util.regex.Matcher

class Okapi extends Authorization {

  public OkapiTenant superTenant


  Okapi(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, debug)
    this.superTenant = superTenant
  }

  def getModuleDescriptor(OkapiTenant reqistryTenant, String moduleId) {
    String url = generateUrl("/_/proxy/modules/${moduleId}")
    Map<String, String> headers = getAuthorizedHeaders(reqistryTenant)

    return restClient.get(url, headers).body
  }

  def getModuleDescriptorFromFolioRegistry(String moduleId) {
    String url = "${Constants.OKAPI_REGISTRY}/_/proxy/modules/${moduleId}"

    return restClient.get(url, [:]).body
  }


  boolean isModuleDescriptorRegistered(String moduleId) {
    String url = generateUrl("/_/proxy/modules?filter=${moduleId}")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    def response = restClient.get(url, headers).body

    return response.size() > 0
  }

  List getUnregisteredModuleDescriptors(List<Map<String, String>> installJson) {
    List unregisteredDescriptors = []

    installJson.each { module ->
      if (!module.id.startsWith("okapi")) {
        if (!isModuleDescriptorRegistered(module.id)) {
          logger.info("Pull module descriptor for '${module.id}' module from FOLIO registry")
          def descriptor = getModuleDescriptorFromFolioRegistry(module.id)
          unregisteredDescriptors.add(descriptor)
        } else {
          logger.info("Skipping module descriptor for '${module.id}'. Already exists in target Okapi")
        }
      }
    }

    return unregisteredDescriptors
  }

  void publishModulesDescriptors(List descriptorsList) {
    if (descriptorsList.isEmpty()) {
      logger.warning("Attempted to publish module descriptors with an empty list. No descriptors were published.")
      return
    }

    String url = generateUrl("/_/proxy/import/modules")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    restClient.post(url, descriptorsList, headers)
    logger.info("Published: ${descriptorsList.size()} descriptors")
  }

  boolean isServiceDiscoveryExists(Map service) {
    String url = generateUrl("/_/discovery/modules/${service['srvcId']}")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    try {
      def response = restClient.get(url, headers).body

      if (response[0].url == service['url']) {
        return true
      } else {
        throw new IllegalArgumentException("Registered module has incorrect url: ${response[0].url}")
      }
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        return false
      } else {
        throw new RequestException("Cannot check existence of service ${service['srvcId']}: ${e.getMessage()}", e.statusCode)
      }
    }
  }

  void publishServiceDiscovery(List discoveryList) {
    String url = generateUrl("/_/discovery/modules")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    logger.info("Registration of service discovery in Okapi. Starting...")
    discoveryList.each { service ->
      if (service['url'] && service['srvcId'] && service['instId']) {
        if (!isServiceDiscoveryExists(service)) {
          logger.info("${service['srvcId']} not registered. Registering...")
          try {
            restClient.post(url, service, headers)
            logger.info("${service['srvcId']} registered successfully")
          } catch (RequestException e) {
            if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
              logger.info("${service['srvcId']} is not registered. ${e.getMessage()}")
              logger.info("Repeat ${service['srvcId']} retry in 3 seconds.")
              sleep(3000)
              restClient.post(url, service, headers)
            } else {
              throw new RequestException("${service['srvcId']} is not registered. ${e.getMessage()}", e.statusCode)
            }
          }
        } else {
          logger.info("${service['srvcId']} already exists")
        }
      } else {
        throw new IllegalArgumentException("${service}: One of required field (srvcId, instId or url) are missing")
      }
    }
    logger.info("Registration of service discovery in Okapi finished successfully.")
  }

  List<String> getServiceDiscoveryIds() {
    String url = generateUrl("/_/discovery/modules")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    def response = restClient.get(url, headers).body

    return response.collect { it.srvcId }
  }

  List getServicesDiscovery() {
    String url = generateUrl("/_/discovery/modules")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    def response = restClient.get(url, headers).body

    return response
  }

  void cleanServicesDiscovery() {
    String url = generateUrl("/_/discovery/modules")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    logger.info("Okapi services discovery cleanup. Starting...")

    restClient.delete(url, headers)

    logger.info("Okapi discovery table cleanup finished successfully.")
  }

  void deleteServiceDiscovery(String srvcId) {
    String url = generateUrl("/_/discovery/modules/${srvcId}")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    restClient.delete(url, headers)
    logger.info("Service ${srvcId} discovery removed.")
  }

  void refreshServicesDiscovery() {
    List discoveryList = getServicesDiscovery()
    discoveryList.collect { service ->
      Matcher match = (service.srvcId =~ /^(.*)-(\d*\.\d*\.\d*.*)$/)
      if (match) {
        def (_, module_name, version) = match[0]
        if (service.url != "http://${module_name}") {
          service.url = "http://${module_name}"
          deleteServiceDiscovery(service.srvcId)
        }
      }
      match.reset()
      // Return the updated service
      service
    }
    if (discoveryList) {
      publishServiceDiscovery(discoveryList)
    }
  }

  String getModuleId(String moduleName) {
    List<String> serviceDiscoveryIds = getServiceDiscoveryIds()

    String moduleId = serviceDiscoveryIds.find { it.startsWith(moduleName) }

    if (moduleId == null) {
      logger.error("Module name not found in service discovery IDs: $moduleName")
    }

    return moduleId
  }

  String getLatestModuleId(String moduleName) {
    String url = generateUrl("/_/proxy/modules?filter=${moduleName}&latest=1")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    def response = restClient.get(url, headers).body

    return response[0].id
  }

  boolean isModuleEnabled(String tenantId, String moduleName) {
    String url = generateUrl("/_/proxy/tenants/${tenantId}/modules")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    def response = restClient.get(url, headers).body

    return response.any { it.id.startsWith(moduleName) }
  }

  List getInstallJson(String tenantId, String action = 'enable') {
    String url = generateUrl("/_/proxy/tenants/${tenantId}/modules")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    def response = restClient.get(url, headers).body

    return response.collect { it + ['action': action] }
  }


  def tenantInstall(OkapiTenant tenant, List installJson, int connectionTimeout = 900000) {
    String url = generateUrl("/_/proxy/tenants/${tenant.tenantId}/install${tenant.getInstallRequestParams()?.toQueryString() ?: ''}")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    logger.info("Install operation for tenant ${tenant.tenantId} started.")
    logger.info("URL: ${url}")

    Instant start = Instant.now()
    def response
    if (installJson) {
      response = restClient.post(url, installJson, headers, [], connectionTimeout).body
    } else {
      logger.warning('installJson list is empty! Nothing to install. Skipping...')
      return
    }
    Instant end = Instant.now()
    Duration duration = Duration.between(start, end)

    logger.info("Install operation for tenant ${tenant.tenantId} took ${duration.toMillis()} milliseconds finished successfully")
    logger.info(JsonOutput.prettyPrint(JsonOutput.toJson(response)))

    return response
  }

  boolean isTenantExist(String tenantId) {
    String url = generateUrl("/_/proxy/tenants/${tenantId}")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    try {
      restClient.get(url, headers)
      logger.info("Tenant ${tenantId} exists")
      return true
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.info("Tenant ${tenantId} is not exists")
        return false
      } else {
        throw new RequestException("Can not able to check tenant ${tenantId} existence: ${e.getMessage()}", e.statusCode)
      }
    }
  }

  void createTenant(OkapiTenant tenant) {
    if (isTenantExist(tenant.tenantId)) {
      logger.warning("Tenant ${tenant.tenantId} already exists!")
      return
    }

    String url = generateUrl("/_/proxy/tenants")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)
    Map body = [id         : tenant.tenantId,
                name       : tenant.tenantName,
                description: tenant.tenantDescription]

    logger.info("Creating tenant ${tenant.tenantId}...")

    restClient.post(url, body, headers)

    logger.info("Tenant (${tenant.tenantId}) successfully created")
  }

  void deleteTenant(OkapiTenant tenant) {
    String url = generateUrl("/_/proxy/tenants/${tenant.tenantId}")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    logger.info("Deleting tenant ${tenant.tenantId}...")

    restClient.delete(url, headers)

    logger.info("Tenant (${tenant.tenantId}) successfully deleted")
  }

  List getTenantsList() {
    String url = generateUrl("/_/proxy/tenants")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    List response = restClient.get(url, headers).body

    return response*.id
  }


  String runIndex(OkapiTenant tenant, Index index) {
    String url = generateUrl("/search/index/inventory/reindex")
    Map<String, String> headers = getAuthorizedHeaders(tenant)
    Map body = [
      "recreateIndex": index.getRecreate(),
      "resourceName" : index.getType()
    ]

    logger.info("[${tenant.getTenantId()}]Starting Elastic Search '${index.getType()}' reindex with recreate flag = ${index.getRecreate()}")

    def response = restClient.post(url, body, headers).body
    String jobId = response.id

    if (index.getWaitComplete()) {
      checkIndexStatus(tenant, jobId)
    }

    return jobId
  }

  void checkIndexStatus(OkapiTenant tenant, String jobId) {
    String url = generateUrl("/instance-storage/reindex/${jobId}")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    steps.timeout(1440) {
      while (true) {
        def response = restClient.get(url, headers).body
        logger.info(JsonOutput.prettyPrint(JsonOutput.toJson(response)))
        if (response.jobStatus == "Ids published") {
          logger.info("Index records to elastic search successfully completed")
          break
        } else {
          logger.info("Waiting timeout, haven't status: Ids published yet.")
          steps.sleep(10)
        }
      }
    }
  }
}
