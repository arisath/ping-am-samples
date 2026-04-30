#!/bin/bash
set -e

/usr/local/ds-install.sh

# 1. Start Tomcat in the background
catalina.sh start

echo "Waiting for AM to deploy at http://localhost:8080/am..."

# 2. Wait for the AM endpoint to be ready (302 is common for unconfigured AM)
max_attempts=30
count=0
until $(curl -s -o /dev/null --head --fail http://localhost:8080/am); do
    if [ $count -eq $max_attempts ]; then
      echo "AM failed to start in time. Checking logs..."
      cat /usr/local/tomcat/logs/catalina.out
      exit 1
    fi
    printf '.'
    attempt_counter=$((attempt_counter+1))
    sleep 5
done

echo -e "\nAM is up. Starting Amster configuration..."

# 3. Execute Amster
# We use a Here-Doc to pass commands to the amster binary
/usr/local/amster/amster <<EOF
install-openam  --acceptLicense \
--serverUrl http://localhost:8080/am \
--adminPwd password \
--cfgDir /home/am/config \
--cookieDomain localhost \
--cfgStorePort 1389 \
--cfgStoreRootSuffix ou=am-config \
--cfgStore dirServer \
--cfgStoreDirMgr uid=am-config,ou=admins,ou=am-config \
--cfgStoreHost localhost \
--cfgStoreAdminPort 4444 \
--cfgStoreSsl SIMPLE \
--cfgStoreDirMgrPwd password \
--userStoreDirMgrPwd password \
 --userStoreHost localhost \
 --userStoreType LDAPv3ForOpenDS \
 --userStorePort 1389 \
 --userStoreSsl SIMPLE \
 --userStoreDirMgr uid=admin \
 --userStoreRootSuffix "dc=example,dc=com"
exit
EOF

echo "Configuration complete."

count=0
echo "Waiting for AM to reach READY state..."

# We check for a 200 OK from the health/ready endpoint
# This confirms the server is up AND the backend stores are connected
until [ "$status" == "200" ]; do
    status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/am/json/health/ready)

    if [ "$status" == "200" ]; then
        echo -e "\n[SUCCESS] AM is fully initialized and ready for traffic."
        break
    fi

    if [ $count -eq $max_attempts ]; then
        echo -e "\n[ERROR] AM failed to reach ready state within timeout."
        echo "Last status code: $status"
        echo "Check /usr/local/tomcat/logs/catalina.out for stack traces."
        exit 1
    fi

    printf "."
    count=$((count+1))
    sleep 5
done


/usr/local/amster/amster <<EOF
connect  -k /home/am/config/security/keys/amster/amster_rsa http://localhost:8080/am
import-config --path /usr/local/amster-config
exit
EOF


# 4. Create test users in the identity store
echo "Creating test users..."
ldapadd -h localhost -p 1389 -D "uid=admin" -w password -c -f /usr/local/test-users.ldif && \
    echo "Test users created." || echo "Test users already exist or failed to create, continuing..."

# 5. Bring Tomcat to foreground to keep container running
# We tail the log so the container output stays active
tail -f /usr/local/tomcat/logs/catalina.out