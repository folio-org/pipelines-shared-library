locals {
  module_configs = {
    "okapi" = {
      "resources" = {
        "requests" = {
          "memory" = "1440Mi"
        },
        "limits" = {
          "memory" = "3072Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-Ddeploy.waitIterations=90"
    }
    "mod-data-import-converter-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0 -Djava.util.logging.config.file=vertx-default-jul-logging.properties"
    },
    "mod-graphql" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = ""
    },
    "mod-finance-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-orders-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-configuration" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-inventory-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-users" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-permissions" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-organizations-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-finance" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-calendar" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-event-config" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-email" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-sender" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-notes" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-tags" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-agreements" = {
      "resources" = {
        "requests" = {
          "memory" = "640Mi"
        },
        "limits" = {
          "memory" = "768Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-server -XX=+UseContainerSupport -XX=MaxRAMPercentage=55.0 -XX=+PrintFlagsFinal"
    },
    "mod-licenses" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-server -XX=+UseContainerSupport -XX=MaxRAMPercentage=50.0 -XX=+PrintFlagsFinal"
    },
    "mod-courses" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-service-interaction" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-server -XX=+UseContainerSupport -XX=MaxRAMPercentage=55.0 -XX=+PrintFlagsFinal"
    },
    "mod-kb-ebsco-java" = {
      "resources" = {
        "requests" = {
          "memory" = "768Mi"
        },
        "limits" = {
          "memory" = "896Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-erm-usage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-invoice-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-inn-reach" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-organizations" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-ldp" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-authtoken" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0 -Dcache.permissions=true"
    },
    "mod-codex-mux" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-password-validator" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-login-saml" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-inventory-update" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-codex-inventory" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-codex-ekb" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-data-export-worker" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-login" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-pubsub" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0 -XX=+HeapDumpOnOutOfMemoryError"
    },
    "mod-circulation-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-template-engine" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-notify" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-feesfines" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-patron-blocks" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-circulation" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-search" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-users-bl" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-rtac" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-erm-usage-harvester" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-user-import" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-ebsconet" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=80.0"
    },
    "mod-audit" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-oai-pmh" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-oa" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-gobi" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-ncip" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-patron" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-eusage-reports" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-copycat" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-z3950" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = ""
    },
    "mod-data-export-spring" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-source-record-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "512Mi"
        },
        "limits" = {
          "memory" = "640Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0 -Djava.util.logging.config.file=vertx-default-jul-logging.properties"
    },
    "mod-inventory" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0 -Dorg.folio.metadata.inventory.storage.type=okapi"
    },
    "mod-orders" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-data-export" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "2048Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-source-record-manager" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0  -Djava.util.logging.config.file=vertx-default-jul-logging.properties"
    },
    "mod-data-import" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0 -Djava.util.logging.config.file=vertx-default-jul-logging.properties"
    },
    "mod-invoice" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-quick-marc" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-remote-storage" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX=MaxRAMPercentage=85.0"
    },
    "edge-rtac" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC -Dokapi_url=http://okapi:9130 -Dsecure_store_props=/etc/edge/ephemeral.properties"
    },
    "edge-oai-pmh" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC -Dokapi_url=http://okapi:9130 -Dsecure_store_props=/etc/edge/ephemeral.properties"
    },
    "edge-patron" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC"
    },
    "edge-orders" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC -Dokapi_url=http://okapi:9130 -Dsecure_store_props=/etc/edge/ephemeral/ephemeral.properties -Dapi_config=/etc/edge/api/api_configuration.json"
    }
    "edge-ncip" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC"
    }
    "edge-dematic" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC -Dokapi_url=http://okapi:9130 -Dsecure_store_props=/etc/edge/ephemeral.properties"
    },
    "edge-caiasoft" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC -Dokapi_url=http://okapi:9130 -Dsecure_store_props=/etc/edge/ephemeral.properties"
    },
    "edge-connexion" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC"
    },
    "edge-inn-reach" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC -Dokapi_url=http://okapi:9130 -Dsecure_store_props=/etc/edge/ephemeral.properties"
    },
    "edge-sip2" = {
      "resources" = {
        "requests" = {
          "memory" = "400Mi"
        },
        "limits" = {
          "memory" = "512Mi"
        }
      },
      "replicaCount" = 1,
      "javaOptions"  = "-XX:MaxRAMPercentage=80.0 -XX:+UseG1GC"
    }
  }
}
