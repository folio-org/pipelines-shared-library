# Use the Jenkins agent base image
FROM jenkins/agent:alpine-jdk17

# Set environment variables to avoid user interaction during installation
ENV DEBIAN_FRONTEND=noninteractive
ARG terraform_ver=1.6.0
ARG helm_ver=3.15.4
ARG kubectl_ver=v1.28.0
ARG kafka_ver=3.8.0

# Install required packages
USER root
RUN apk update && \
    apk add --no-cache \
    aws-cli \
    postgresql-client \
    unzip
# Install Terraform
RUN curl -fsSL "https://releases.hashicorp.com/terraform/${terraform_ver}/terraform_${terraform_ver}_linux_amd64.zip" -o terraform.zip && \
    unzip terraform.zip && \
    mv terraform /usr/local/bin/ && \
    rm terraform.zip &&\
# Install Helm
    curl -fsSL "https://get.helm.sh/helm-v${helm_ver}-linux-amd64.tar.gz" -o helm.tar.gz && \
    tar -zxvf helm.tar.gz && \
    mv linux-amd64/helm /usr/local/bin/helm && \
    rm -rf linux-amd64 helm.tar.gz &&\
# Install Kafka client
    curl -fsSL "https://archive.apache.org/dist/kafka/${kafka_ver}/kafka_2.13-${kafka_ver}.tgz" -o kafka_2.13-${kafka_ver}.tgz && \
    tar -xzf kafka_2.13-${kafka_ver}.tgz && \
    mv kafka_2.13-${kafka_ver} /opt/kafka && \
	rm -rf kafka_2.13-${kafka_ver}.tgz &&\
# Install Kubectl
    curl -LO "https://dl.k8s.io/release/${kubectl_ver}/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && \
	mv kubectl /usr/local/bin/

# Set the working directory
WORKDIR /home/jenkins






