# Format for Edge modules tenant configuration
#<module_name> = {
#  <tenant_name> = "<username>,<password>",
#  ...
#  <tenant_name> = "<username>,<password>",
#  }
#}
locals {
  edge_ephemeral_config = {
    "edge-rtac" = {},
    "edge-oai-pmh" = {
      "test_oaipmh" = "test-user,test"
    },
    "edge-patron" = {},
    "edge-orders" = {
      "test_edge_orders" = "test-user,test",
    }
    "edge-ncip" = {}
    "edge-dematic" = {
      (var.tenant_id) = "stagingDirector,${var.tenant_id}"
    },
    "edge-caiasoft" = {
      (var.tenant_id) = "caiaSoftClient,${var.tenant_id}"
    },
    "edge-connexion" = {}
  }
}
