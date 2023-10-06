secureStore.type=Ephemeral
# a comma separated list of tenants
tenants=<% out << (edge_tenants) %>
# a comma separated list of tenants mappings in form X-TO-CODE:tenant, where X-TO-CODE it's InnReach Header value
tenantsMappings=fli01:<% out << (edge_mappings) %>
#######################################################
# For each tenant, the institutional user password...
#
# Note: this is intended for development purposes only
#######################################################
<% out << (edge_users) %>
<% out << (institutional_users) %>
