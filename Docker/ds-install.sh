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
 --profile am-identity-store \
 --set am-identity-store/amIdentityStoreAdminPassword:password \
 --set am-identity-store/backendName:userData \
 --set am-identity-store/baseDn:ou=identities \
 --acceptLicense \
 --profile am-cts \
 --hostname localhost \
 --set am-cts/amCtsAdminPassword:password

/usr/local/opendj/bin/start-ds

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

