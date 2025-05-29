# This Dockerfile is used to create a Java container for Jenkins
ARG JAVA_VERSION=21

FROM amazoncorretto:${JAVA_VERSION}-alpine-jdk

ENV JMX_AGENT_VERSION=0.19.0
ENV JMX_AGENT_PATH=/jmx_exporter

# Install git
RUN apk update && apk add --no-cache git \
  && mkdir -p $JMX_AGENT_PATH \
  && wget -q -O "$JMX_AGENT_PATH/jmx_prometheus_javaagent.jar" \
  "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/$JMX_AGENT_VERSION/jmx_prometheus_javaagent-$JMX_AGENT_VERSION.jar" \
  && echo -e 'rules:\n- pattern: ".*"' > "${JMX_AGENT_PATH}/jmx_prometheus_config.yaml"
