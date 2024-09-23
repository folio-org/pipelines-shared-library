package org.folio.rest_v2.eureka.kong

import org.folio.models.EurekaNamespace
import org.folio.models.EurekaTenant
import org.folio.models.User
import org.folio.utilities.Tools

class Edge extends Users {
  Edge(Object context, String kongUrl, String keycloakUrl) {
    super(context, kongUrl, keycloakUrl)
  }

  final public String EUREKA_EDGE_USERS_CONFIG = 'edge/config_eureka.yaml'

  def createEurekaUsers(EurekaNamespace namespace) {

    tools.copyResourceFileToCurrentDirectory(EUREKA_EDGE_USERS_CONFIG)
    def userData = context.readYaml file: './config_eureka.yaml'

    namespace.getTenants().each { tenantId, tenant ->

      Map headers = getTenantHttpHeaders(tenant as EurekaTenant)

      def capabilities = restClient.get(super.generateUrl("/capabilities?limit=5000"), headers).body
      def capabilitiesSets = restClient.get(super.generateUrl("/capability-sets?limit=5000"), headers).body

      List caps = []
      List capSets = []

      userData.each { name, user ->

        if (user['tenants'] && user['tenants'][0]['create']) {

          if (user['capabilities']) {
            user['capabilities'].each { defaultCap ->
              caps.add((capabilities.capabilities.find { it -> it['name'] == defaultCap })['id'])
            }
          }
          if (user['capabilitiesSet']) {
            user['capabilitiesSet'].each { defaultCapSet ->
              capSets.add((capabilitiesSets.capabilitySets.find { it -> it['name'] == defaultCapSet })['id'])
            }
          }

          User edgeUser = new User()
          edgeUser.setUsername(user['tenants'][0]['username'] as String)
          edgeUser.setFirstName(user['tenants'][0]['firstName'] as String)
          edgeUser.setLastName(user['tenants'][0]['lastName'] as String)
          edgeUser.setActive(true)
          edgeUser.setType('system')
          edgeUser.setEmail(user['tenants'][0]['username'] + '@ci.folio.org')
          edgeUser.setPreferredContactTypeId('002')

          def response = restClient.post(generateUrl("/users-keycloak/users"), edgeUser.toMap(), headers).body

          logger.info(user['tenants'][0]['username'] + " users created...")

          Map userPass = [
            username: user['tenants'][0]['username'],
            userId  : response['id'],
            password: user['tenants'][0]['password']
          ]

          restClient.post(generateUrl("/authn/credentials"), userPass, headers)

          logger.info(user['tenants'][0]['username'] + " user password reset...")

          if (caps) {
            Map userCaps = [
              userId       : response['id'],
              capabilityIds: caps
            ]

            restClient.post(generateUrl("/users/capabilities"), userCaps, headers)

            logger.info(user['tenants'][0]['username'] + " user has assigned capabilities...")
          }
          if (capSets) {
            Map userCapsSets = [
              userId          : response['id'],
              capabilitySetIds: capSets
            ]

            restClient.post(generateUrl("/users/capability-sets"), userCapsSets, headers)

            logger.info(user['tenants'][0]['username'] + " user has assigned capabilitiesSets...")
          }
        }
      }
    }
  }
}
