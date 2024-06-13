#!/usr/bin/env bash

script="kc_init_script.sh"
keycloakUrl="${KC_URL:-http://localhost:8080}"

maxAttempts=10
attemptCounter=0

function loginAsAdmin() {
  echo "$(date +%F' '%T,%3N) INFO  [$script] Logging into Keycloack Service as admin user in master realm [attempt: $attemptCounter]"
  /opt/keycloak/bin/kcadm.sh config credentials \
    --server "$keycloakUrl" \
    --realm master \
    --user ${KEYCLOAK_ADMIN} \
    --password "${KEYCLOAK_ADMIN_PASSWORD}" \
    &> /dev/null
}

# Make a small pause and let Keycloak to start first
sleep 20

while [ $attemptCounter -le $maxAttempts ]; do
  echo "$(date +%F' '%T,%3N) INFO  [$script] Turning off SSL connection requirement for Management WEB Console in master realm [attempt: $attemptCounter]"
  if loginAsAdmin; then
    /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=NONE
    break
  fi
  echo "$(date +%F' '%T,%3N) INFO  [$script] Keycloak is not ready yet, waiting for 10 seconds [attempt: $attemptCounter]"
  attemptCounter=$((attemptCounter + 1))
  sleep 10
done

if [ $attemptCounter -ge $maxAttempts ]; then
  echo "$(date +%F' '%T,%3N) WARN  [$script] Failed to switch off SSL connection requirement for Management WEB Console, the amount of attempts is greater than $maxAttempts."
  exit 1
fi
