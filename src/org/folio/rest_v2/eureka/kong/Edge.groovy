package org.folio.rest_v2.eureka.kong

import org.folio.models.EurekaNamespace
import org.folio.models.EurekaTenant
import org.folio.models.User

class Edge extends Users {
  Edge(Object context, String kongUrl, String keycloakUrl) {
    super(context, kongUrl, keycloakUrl)
  }

  final public String EUREKA_EDGE_USERS_CONFIG = 'edge/config_eureka.yaml'

  def createEurekaUsers(EurekaNamespace namespace) {

    super.context.tools.copyResourceFileToCurrentDirectory(EUREKA_EDGE_USERS_CONFIG)
    def tenants = super.restClient.get(super.generateUrl("/tenants")).body
    def userData = super.context.readYaml file: './config_eureka.yaml'

    namespace.getTenants().each { tenant ->

      Map headers = super.getTenantHttpHeaders(tenant as EurekaTenant, true)

      def capabilities = super.restClient.get(super.generateUrl("/capabilities?limit=5000"), headers as Map<String, String>).body
      def capabilitiesSets = super.restClient.get(super.generateUrl("/capability-sets?limit=5000"), headers as Map<String, String>).body

      List caps = []
      List capSets = []

      userData.each { user ->
        if (user['capabilities']) {
          user['capabilities'].each { defaultCap ->
            caps.add(capabilities.capabilities.find { it -> it['name'] == defaultCap }['id'])
          }
        }
        if (user['capabilitiesSet']) {
          user['capabilitiesSet'].each { defaultCapSet ->
            capSets.add(capabilitiesSets.capabilities.find { it -> it['name'] == defaultCapSet }['id'])
          }
        }

        User edgeUser = new User()
        edgeUser.setUsername(user['username'] as String)
        edgeUser.setFirstName(user['firstName'] as String)
        edgeUser.setLastName(user['lastName'] as String)
        edgeUser.setActive(true)
        edgeUser.setType('system')
        edgeUser.setEmail('edgeUser@ci.folio.org')
        edgeUser.setPreferredContactTypeId('002')

        def response = super.restClient.post(super.generateUrl("/users-keycloak/users"), headers, edgeUser.toMap() as Map<String, String>).body['id']

        Map userPass = [
          username: user['username'],
          id      : response,
          password: user['password']
        ]

        super.restClient.post(super.generateUrl("/authn/credentials"), headers, userPass as Map<String, String>)

        if (caps) {
          Map userCaps = [
            userId       : response,
            capabilityIds: caps
          ]

          super.restClient.post(super.generateUrl("/users/capabilities"), headers, userCaps as Map<String, String>)

        }

        Map userCapsSets = [
          userId            : response,
          "capabilitySetIds": capSets
        ]

        super.restClient.post(super.generateUrl("/users/capability-sets"), headers, userCapsSets as Map<String, String>)

      }
    }
  }
}
