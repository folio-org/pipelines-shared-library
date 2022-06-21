#locals {
#  performance    = local.module_configs_perf
#  testing        = local.module_configs_test
#  development    = local.module_configs_dev
#  module_configs = var.env_type == "development" ? local.development : (var.env_type == "performance" ? local.performance : local.testing)
#}

locals {
  module_configs_dev = {
    "okapi" = {
      resources = {
        requests = {
          memory = "1440Mi"
        },
        limits = {
          memory = "3072Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-Ddeploy.waitIterations=90 --add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED -Dloglevel=INFO -Dport=9131 -Dokapiurl=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dstorage=postgres -Dpostgres_host=$DB_HOST -Dpostges_port=$DB_PORT -Dpostgres_username=$DB_USERNAME -Dpostgres_password=$DB_PASSWORD -Dpostgres_database=$DB_NAME $JAVA_OPTS_HEAP_DUMP -XX:MetaspaceSize=384m -XX:MaxMetaspaceSize=512m -Xmx922m"
      javaArgs     = "cluster  -cluster-host $CLUSTER_HOST -hazelcast-config-url https://$S3_DOMAIN/$CLUSTER-$DOMAIN_PREFIX-$REGION-$ACCOUNT_NAME/okapi/hazelcast-ecs.xml"
    },
    "edge-sip2" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
      javaArgs     = "-conf /usr/ms/sip2.conf"
    },
    "edge-caiasoft" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0 -Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties"
      javaArgs     = "--server.port=$CONTAINER_PORT --log.level=info"
    },
    "edge-connexion" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
      javaArgs     = "-Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties -Dhttp.port=$INTERNAL_DOCKER_PORT -Dlog.level=debug"
    },
    "edge-dematic" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties"
      javaArgs     = "-Dport=$CONTAINER_PORT"
    },
    "edge-ea-data-export" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
      javaArgs     = "-Dlog_level=DEBUG -Dport=8081 -Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties -Drequest_timeout_ms2=30000 -Dtoken_cache_ttl_ms=300000 -Dnull_token_cache_ttl_ms=30000 -Dtoken_cache_capacity=25"
    },
    "edge-inn-reach" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0 -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties"
    },
    "edge-ncip" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
      javaArgs     = "-Dlog_level=DEBUG -Dport=8081 -Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties -Drequest_timeout_ms=30000 -Dtoken_cache_ttl_ms=300000 -Dnull_token_cache_ttl_ms=30000 -Dtoken_cache_capacity=25"
    },
    "edge-oai-pmh" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "$JAVA_OPTS_VERTEX_LOGGER $JAVA_OPTS_HEAP_DUMP $JAVA_OPTS_META_SPACE -Xmx952m"
      javaArgs     = "-Dlog_level=DEBUG -Dport=8081 -Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties -Drequest_timeout_ms=86400000 -Dtoken_cache_ttl_ms=300000 -Dnull_token_cache_ttl_ms=30000 -Dtoken_cache_capacity=25"
    },
    "edge-orders" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
      javaArgs     = "-Dlog_level=DEBUG -Dport=8081 -Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties -Drequest_timeout_ms2=30000 -Dtoken_cache_ttl_ms=300000 -Dnull_token_cache_ttl_ms=30000 -Dtoken_cache_capacity=25"
    },
    "edge-patron" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
      javaArgs     = "-Dlog_level=DEBUG -Dport=8081 -Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties -Drequest_timeout_ms=30000 -Dtoken_cache_ttl_ms=300000 -Dnull_token_cache_ttl_ms=30000 -Dtoken_cache_capacity=25"
    },
    "edge-rtac" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
      javaArgs     = "-Dlog_level=DEBUG -Dport=8081 -Dokapi_url=http://pvt.lb.$CLUSTER.$DOMAIN_PREFIX.$REGION:$OKAPI_PORT -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties -Drequest_timeout_ms=30000 -Dtoken_cache_ttl_ms=300000 -Dnull_token_cache_ttl_ms=30000 -Dtoken_cache_capacity=25"
    },
    "edge-sftp" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
    },
    "mod-aes" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX:MaxRAMPercentage=85.0"
      javaArgs     = "$JAVA_ARGS -Dkafka.url=aes-kafka.$CLUSTER.folio-eis.$REGION:9092"
    },
    "mod-data-import-converter-storage" = {
      resources = {
        requests = {
          memory = "1024Mi"
        },
        limits = {
          memory = "2048Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0 -Djava.util.logging.config.file=vertx-default-jul-logging.properties"
    },
    "mod-graphql" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = ""
    },
    "mod-finance-storage" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-orders-storage" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-configuration" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-inventory-storage" = {
      resources = {
        requests = {
          cpu    = "256m"
          memory = "896Mi"
        },
        limits = {
          cpu    = "512m"
          memory = "1440Mi"
        }
      },
      replicaCount  = 2,
      javaOptions   = "-XX:MaxRAMPercentage=85.0 -Dlog4j2.formatMsgNoLookups=true"
      dbMaxPoolSize = "5"
    },
    "mod-users" = {
      resources = {
        requests = {
          memory = "1024Mi"
        },
        limits = {
          memory = "2048Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-permissions" = {
      resources = {
        requests = {
          memory = "1024Mi"
        },
        limits = {
          memory = "2048Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-organizations-storage" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-finance" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-calendar" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-event-config" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-email" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-sender" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-notes" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-tags" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-agreements" = {
      resources = {
        requests = {
          memory = "640Mi"
        },
        limits = {
          memory = "768Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-server -XX=+UseContainerSupport -XX=MaxRAMPercentage=85.0 -XX=+PrintFlagsFinal"
    },
    "mod-licenses" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-server -XX=+UseContainerSupport -XX=MaxRAMPercentage=85.0 -XX=+PrintFlagsFinal"
    },
    "mod-courses" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-service-interaction" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-server -XX=+UseContainerSupport -XX=MaxRAMPercentage=85.0 -XX=+PrintFlagsFinal"
    },
    "mod-kb-ebsco-java" = {
      resources = {
        requests = {
          memory = "1024Mi"
        },
        limits = {
          memory = "2048Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-erm-usage" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-invoice-storage" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-inn-reach" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-organizations" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-ldp" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-authtoken" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0 -Dcache.permissions=true"
    },
    "mod-codex-mux" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-password-validator" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-login-saml" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-inventory-update" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-codex-inventory" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-codex-ekb" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-data-export-worker" = {
      resources = {
        requests = {
          memory = "1024Mi"
        },
        limits = {
          memory = "2048Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-login" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-pubsub" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0 -XX=+HeapDumpOnOutOfMemoryError"
    },
    "mod-circulation-storage" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-template-engine" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-notify" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-feesfines" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-patron-blocks" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-circulation" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-search" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-users-bl" = {
      resources = {
        requests = {
          memory = "1024Mi"
        },
        limits = {
          memory = "2048Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-rtac" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-erm-usage-harvester" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-user-import" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-ebsconet" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-audit" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-oai-pmh" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-oa" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-gobi" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-ncip" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-patron" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-eusage-reports" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-copycat" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-z3950" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = ""
    },
    "mod-data-export-spring" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-source-record-storage" = {
      resources = {
        requests = {
          cpu    = "256m"
          memory = "896Mi"
        },
        limits = {
          cpu    = "512m"
          memory = "1440Mi"
        }
      },
      replicaCount  = 1,
      javaOptions   = "-XX=MaxRAMPercentage=85.0 -Dlog4j2.formatMsgNoLookups=true"
      dbMaxPoolSize = "15"
    },
    "mod-inventory" = {
      resources = {
        requests = {
          cpu    = "256m"
          memory = "1440Mi"
        },
        limits = {
          cpu    = "512m"
          memory = "1872Mi"
        }
      },
      replicaCount  = 1,
      javaOptions   = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC -Dorg.folio.metadata.inventory.storage.type=okapi"
      dbMaxPoolSize = "5"
    },
    "mod-orders" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-data-export" = {
      resources = {
        requests = {
          memory = "1024Mi"
        },
        limits = {
          memory = "2048Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-source-record-manager" = {
      resources = {
        requests = {
          memory = "896Mi"
          cpu    = "256m"
        },
        limits = {
          memory = "1440Mi"
          cpu    = "512m"
        }
      },
      replicaCount        = 1,
      javaOptions         = "-XX=MaxRAMPercentage=85.0 -Dlog4j2.formatMsgNoLookups=true"
      dbMaxPoolSize       = "15"
      dbReconnectinterval = "1000"
      dbReconnectattempts = "3"
    },
    "mod-data-import" = {
      resources = {
        requests = {
          cpu    = "128m"
          memory = "1024Mi"
        },
        limits = {
          cpu    = "192m"
          memory = "2048Mi"
        }
      },
      replicaCount  = 1,
      javaOptions   = "-XX:MaxRAMPercentage=85.0 -XX:+UseG1GC"
      dbMaxPoolSize = "5"
    },
    "mod-invoice" = {
      resources = {
        requests = {
          cpu    = "256m"
          memory = "890"
        },
        limits = {
          cpu    = "512m"
          memory = "1440Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0 -Dlog4j2.formatMsgNoLookups=true"
    },
    "mod-quick-marc" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    },
    "mod-remote-storage" = {
      resources = {
        requests = {
          memory = "400Mi"
        },
        limits = {
          memory = "512Mi"
        }
      },
      replicaCount = 1,
      javaOptions  = "-XX=MaxRAMPercentage=85.0"
    }
  }
}
