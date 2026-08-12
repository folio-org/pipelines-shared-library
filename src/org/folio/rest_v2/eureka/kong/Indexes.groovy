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

  Indexes(def context, String kongUrl, String keycloakUrl, boolean debug = false){
    super(context, kongUrl, keycloakUrl, debug)
  }

  Indexes(Kong kong){
    this(kong.context, kong.kongUrl, kong.keycloak, kong.getDebug())
  }

  //TODO: Unify instance vs. generic index flow so both return the same type
  String runIndexFlow(EurekaTenant tenant, Index index) {
    if (index.getType() == 'instance') {
      runInstanceIndex(tenant)
      return null   // instance reindex has no job-id concept; callers must not rely on return value
    }
    return runIndex(tenant, index)
  }

  Indexes runInstanceIndex(EurekaTenant tenant){
    logger.info("Perform instance index on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/search/index/instance-records/reindex/full")

    Map body = [
      "indexSettings": []
    ]

    try {
      restClient.post(url, body, headers).body
    } catch (Exception e) {
      if (e.getMessage()?.contains('Reindex is already in progress')) {
        logger.warning("[${tenant.getTenantId()}] Instance reindex is already in progress, skipping: ${e.getMessage()}")
        return this
      }
      throw e
    }

    return this
  }

  String runIndex(EurekaTenant tenant, Index index) {
    logger.info("Perform index on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/search/index/inventory/reindex")

    Map body = [
      "recreateIndex": index.getRecreate(),
      "resourceName" : index.getType()
    ]

    logger.info("[${tenant.getTenantId()}] Starting Elastic Search '${index.getType()}' reindex with recreate flag = ${index.getRecreate()}")

    def responseBody = restClient.post(url, body, headers).body

    // Guard: some reindex endpoints return HTTP 200 with an empty/null body
    // (reindex was accepted and started) but no job id is provided.
    String jobId = responseBody?.id

    if (!jobId) {
      logger.warning("[${tenant.getTenantId()}] Reindex POST for '${index.getType()}' succeeded but " +
        "response body contained no 'id' field (body=${responseBody}). " +
        "Reindex has started; status polling will be skipped.")
      return null
    }

    logger.info("[${tenant.getTenantId()}] Reindex job started with id: ${jobId}")

    if (index.getWaitComplete()) {
      checkIndexStatus(tenant, jobId)
    }

    return jobId
  }

  void checkIndexStatus(EurekaTenant tenant, String jobId) {
    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/authority-storage/reindex/${jobId}")

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
