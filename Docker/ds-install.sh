/usr/local/opendj/setup \
 --deploymentId AVD1SbAikgKUsNy3NXu2l_1LgDTkTWA5CBVN1bkVDAIRJr0sU9KU0TmY \
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
 --acceptLicense \
--profile am-cts \
--hostname localhost \
 --set am-cts/amCtsAdminPassword:password

/usr/local/opendj/bin/start-ds

/usr/local/opendj/bin/dsconfig set-password-policy-prop   --policy-name "Default Password Policy"   --set 'require-secure-authentication:false'   --hostname localhost   --port 4444   --bindDN "uid=admin"   --bindPassword password   --trustAll   --no-prompt

/usr/local/opendj/bin/dsconfig set-password-policy-prop   --policy-name "Root Password Policy"     --set 'require-secure-authentication:false' --set allow-pre-encoded-passwords:true   --hostname localhost   --port 4444   --bindDN "uid=admin"   --bindPassword password   --trustAll   --no-prompt


/usr/local/opendj/bin/ldapmodify --hostname localhost --port 1389 \
  --bindDN "uid=admin" --bindPassword password --add <<EOF
dn: ou=people,dc=example,dc=com
objectClass: top
objectClass: organizationalUnit
ou: people

dn: ou=groups,dc=example,dc=com
objectClass: top
objectClass: organizationalUnit
ou: groups
EOF