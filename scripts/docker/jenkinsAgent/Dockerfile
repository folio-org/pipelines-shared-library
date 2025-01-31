# -------------------------------------------------------------------
# 1) Builder Stage
#    - Uses a plain Alpine image to download & extract tools.
# -------------------------------------------------------------------
FROM alpine:3.21 AS builder

ARG TERRAFORM_VERSION="1.6.6"
ARG HELM_VERSION="3.15.4"
ARG KUBECTL_VERSION="1.28.0"
ARG KAFKA_VERSION="3.8.0"
ARG SCALA_VERSION="2.13"

# Install only what is needed to download & extract
RUN apk add --no-cache curl unzip tar gzip

# Create a directory to hold final extracted artifacts
RUN mkdir /dist

# --- Terraform ---
RUN curl -fsSL "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip" \
    -o /tmp/terraform.zip \
 && unzip /tmp/terraform.zip -d /dist \
 && rm /tmp/terraform.zip

# --- Helm ---
RUN curl -fsSL "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz" \
    -o /tmp/helm.tar.gz \
 && tar xzf /tmp/helm.tar.gz \
 && mv linux-amd64/helm /dist/helm \
 && rm -rf /tmp/helm.tar.gz linux-amd64

# --- kubectl ---
RUN if [ "$KUBECTL_VERSION" = "latest" ]; then \
      KUBECTL_VERSION="$(curl -L -s https://dl.k8s.io/release/stable.txt)"; \
    fi \
 && curl -LO "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
 && chmod +x kubectl \
 && mv kubectl /dist/

# --- Kafka ---
RUN curl -fsSL "https://downloads.apache.org/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz" \
    -o /tmp/kafka.tgz \
 && tar xzf /tmp/kafka.tgz -C /tmp \
 && mv /tmp/kafka_${SCALA_VERSION}-${KAFKA_VERSION} /dist/kafka \
 && rm -f /tmp/kafka.tgz

# At this point, /dist contains:
#   - terraform
#   - helm
#   - kubectl
#   - kafka/ (full Kafka client directory)

# -------------------------------------------------------------------
# 2) Final Stage
#    - Jenkins inbound agent (Alpine + JDK17)
#    - Install only runtime packages (bash, aws-cli, docker-cli, etc.)
#    - Copy final binaries from builder.
# -------------------------------------------------------------------
FROM jenkins/inbound-agent:latest-alpine3.21-jdk17

USER root

# Install runtime packages and update CA certs
RUN apk update && apk add --no-cache \
    bash \
    curl \
    unzip  \
    tar  \
    gzip  \
    gnupg \
    ca-certificates \
    openssl \
    coreutils \
    docker-cli \
    aws-cli \
    postgresql-client \
    && update-ca-certificates

# Copy the downloaded binaries/artifacts from builder
COPY --from=builder /dist/terraform /usr/local/bin/terraform
COPY --from=builder /dist/helm     /usr/local/bin/helm
COPY --from=builder /dist/kubectl  /usr/local/bin/kubectl

# Copy entire Kafka directory to /opt and symlink the scripts
COPY --from=builder /dist/kafka /opt/kafka
RUN ln -s /opt/kafka/bin/* /usr/local/bin/ \
    && chmod +x /opt/kafka/bin/*

# Switch back to Jenkins user
USER jenkins

# The inbound agent's default entrypoint/cmd handles JNLP connection
