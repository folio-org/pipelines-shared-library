package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import org.folio.models.EurekaTenant
import org.folio.models.Index
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

class Indexes extends Kong{

  Indexes(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, kongUrl, keycloak, debug)
  }

  Indexes(def context, String kongUrl, String keycloakUrl, int keycloakTTL = -100, boolean debug = false){
    super(context, kongUrl, keycloakUrl, keycloakTTL, debug)
  }

  Indexes(Kong kong){
    this(kong.context, kong.kongUrl, kong.keycloak, kong.getDebug())
  }

  String runIndex(EurekaTenant tenant, Index index) {
    logger.info("Perform index on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/search/index/inventory/reindex")

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

  void checkIndexStatus(EurekaTenant tenant, String jobId) {
    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/instance-storage/reindex/${jobId}")

    context.timeout(1440) {
      while (true) {
        def response = restClient.get(url, headers).body

        logger.info(JsonOutput.prettyPrint(JsonOutput.toJson(response)))

        if (response.jobStatus == "Ids published") {
          logger.info("Index records to elastic search successfully completed")

          break
        } else {
          logger.info("Waiting timeout, haven't status: Ids published yet.")

          context.sleep(10)
        }
      }
    }
  }

  @NonCPS
  static Indexes get(Kong kong){
    return new Indexes(kong)
  }
}
