# This Dockerfile is used to create a Java container for Jenkins
ARG JAVA_VERSION=21

FROM amazoncorretto:${JAVA_VERSION}-alpine-jdk

# Install git
RUN apk update && apk add --no-cache git
