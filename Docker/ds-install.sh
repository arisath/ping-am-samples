rm -rf /usr/local/opendj/changelogDb \
        /usr/local/opendj/locks \
        /usr/local/opendj/logs \
        /usr/local/opendj/var \
        /usr/local/opendj/bak \
        /usr/local/opendj/import-tmp

DEPLOYMENT_ID=$(/usr/local/opendj/bin/dskeymgr create-deployment-id -w password 2>/dev/null | tail -1)

/usr/local/opendj/setup \
 --deploymentId "$DEPLOYMENT_ID" \
 --deploymentIdPassword password \
 --rootUserDN uid=admin \
 --rootUserPassword password \
 --monitorUserPassword password \
 --adminConnectorPort 4444 \
 --ldapPort 1389 \
 --enableStartTls \
 --ldapsPort 1636 \
 --httpsPort 8443 \
 --profile am-config \
 --set am-config/amConfigAdminPassword:password \
 --profile ds-user-data \
 --set ds-user-data/backendName:userData \
 --set ds-user-data/addBaseEntry:true \
 --set ds-user-data/baseDn:dc=example,dc=com \
 --profile am-cts \
 --hostname localhost \
 --set am-cts/amCtsAdminPassword:password \
 --acceptLicense

/usr/local/opendj/bin/start-ds

echo "Waiting for DS to be ready..."
until /usr/local/opendj/bin/ldapsearch \
  --hostname localhost --port 4444 \
  --bindDN "uid=admin" --bindPassword password \
  --useSsl --trustAll \
  --baseDN "" --searchScope base "(objectClass=*)" 1.1 > /dev/null 2>&1; do
  sleep 2
done
sleep 3
echo "DS is ready."

/usr/local/opendj/bin/dsconfig set-password-policy-prop \
  --policy-name "Default Password Policy" \
  --set 'require-secure-authentication:false' \
  --set 'require-secure-password-changes:false' \
  --hostname localhost --port 4444 \
  --bindDN "uid=admin" --bindPassword password \
  --trustAll --no-prompt

/usr/local/opendj/bin/dsconfig set-password-policy-prop \
  --policy-name "Root Password Policy" \
  --set 'require-secure-authentication:false' \
  --set 'require-secure-password-changes:false' \
  --set allow-pre-encoded-passwords:true \
  --hostname localhost --port 4444 \
  --bindDN "uid=admin" --bindPassword password \
  --trustAll --no-prompt

printf 'dn: ou=people,dc=example,dc=com\nchangetype: add\nobjectClass: top\nobjectClass: organizationalUnit\nou: people\n' > /tmp/people.ldif
printf 'dn: ou=groups,dc=example,dc=com\nchangetype: add\nobjectClass: top\nobjectClass: organizationalUnit\nou: groups\n' > /tmp/groups.ldif

/usr/local/opendj/bin/ldapmodify --hostname localhost --port 1389 \
  --bindDN "uid=admin" --bindPassword password \
  --filename /tmp/people.ldif

/usr/local/opendj/bin/ldapmodify --hostname localhost --port 1389 \
  --bindDN "uid=admin" --bindPassword password \
  --filename /tmp/groups.ldif


/usr/local/opendj/bin/dsconfig get-password-policy-prop --policy-name "Default Password Policy" --property password-validator --hostname localhost --port 4444 --bindDN "uid=admin" --bindPassword password --no-prompt --trustAll                                                                                                                                                          
                                