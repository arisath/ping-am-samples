#!/bin/bash
set -e
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

# 4. Bring Tomcat to foreground to keep container running
# We tail the log so the container output stays active
tail -f /usr/local/tomcat/logs/catalina.out