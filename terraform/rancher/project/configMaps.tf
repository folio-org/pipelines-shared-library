# resource "kubernetes_config_map" "log4j_config" {
#   metadata {
#     name      = "log4j-config"
#     namespace = rancher2_namespace.this.name
#   }
#
#   data = {
#     "log4j2.properties" = <<-EOF
#       status = error
#       name = PropertiesConfig
#       packages = org.folio.okapi.common.logging,org.folio.spring.logging
#
#       filters = threshold
#
#       filter.threshold.type = ThresholdFilter
#       filter.threshold.level = info
#
#       appenders = console
#
#       appender.console.type = Console
#       appender.console.name = STDOUT
#       appender.console.layout.type = PatternLayout
#       appender.console.layout.pattern = %d{yyyy-MM-dd'T'HH:mm:ss.SSSZ} [$${FolioLoggingContext:requestid:-$${folio:requestid:-}}] [$${FolioLoggingContext:tenantid:-$${folio:tenantid:-}}] [$${FolioLoggingContext:userid:-$${folio:userid:-}}] [$${FolioLoggingContext:moduleid:-$${folio:moduleid:-}}] %-5p %-20.20C{1} %m%n
#
#       rootLogger.level = info
#       rootLogger.appenderRefs = info
#       rootLogger.appenderRef.stdout.ref = STDOUT
#     EOF
#   }
# }
