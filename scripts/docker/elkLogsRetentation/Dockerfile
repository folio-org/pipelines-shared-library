FROM alpine:3.18

RUN apk add --no-cache \
    bash \
    curl \
    coreutils \
    ca-certificates

RUN addgroup -g 1001 cleanup && \
    adduser -D -u 1001 -G cleanup cleanup

WORKDIR /app

COPY cleanup.sh /app/cleanup.sh

RUN chmod +x /app/cleanup.sh && \
    sed -i 's/\r$//' /app/cleanup.sh

RUN chown -R cleanup:cleanup /app

USER cleanup

ENTRYPOINT ["/bin/bash", "/app/cleanup.sh"]
