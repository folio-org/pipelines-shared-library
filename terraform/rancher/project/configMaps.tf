resource "kubernetes_config_map" "log4j_config" {
  metadata {
    name      = "log4j-config"
    namespace = rancher2_namespace.this.name
  }

  data = {
    "log4j2.properties" = <<-EOF
      status=error
      name=PropertiesConfig
      packages=org.folio.des.util
      filters=threshold
      filter.threshold.type=ThresholdFilter
      filter.threshold.level=info
      appenders=console
      appender.console.type=Console
      appender.console.name=STDOUT
      appender.console.layout.type=JSONLayout
      appender.console.layout.compact=true
      appender.console.layout.eventEol=true
      appender.console.layout.stacktraceAsString=true
      appender.console.layout.includeTimeMillis=true
      appender.console.layout.requestId.type=KeyValuePair
      appender.console.layout.requestId.key=requestId
      appender.console.layout.requestId.value=$${folio:requestid:-}
      appender.console.layout.tenantId.type=KeyValuePair
      appender.console.layout.tenantId.key=tenantId
      appender.console.layout.tenantId.value=$${folio:tenantid:-}
      appender.console.layout.userId.type=KeyValuePair
      appender.console.layout.userId.key=userId
      appender.console.layout.userId.value=$${folio:userid:-}
      appender.console.layout.moduleId.type=KeyValuePair
      appender.console.layout.moduleId.key=moduleId
      appender.console.layout.moduleId.value=$${folio:moduleid:-}
      rootLogger.level=info
      rootLogger.appenderRefs=info
      rootLogger.appenderRef.stdout.ref=STDOUT
    EOF
  }
}
