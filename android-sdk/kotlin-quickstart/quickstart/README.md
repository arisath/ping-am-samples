./ldapsearch -h localhost -p 1389  -D "uid=admin"  -w password       --baseDn "dc=example,dc=com"  "(uid=test)"


dn: uid=test,ou=people,dc=example,dc=com
objectclass: iplanet-am-managed-person
objectclass: inetuser
objectclass: sunFMSAML2NameIdentifier
objectclass: inetorgperson
objectclass: devicePrintProfilesContainer
objectclass: boundDevicesContainer
objectclass: iplanet-am-user-service
objectclass: iPlanetPreferences
objectclass: pushDeviceProfilesContainer
objectclass: forgerock-am-dashboard-service
objectclass: organizationalperson
objectclass: top
objectclass: kbaInfoContainer
objectclass: person
objectclass: sunAMAuthAccountLockout
objectclass: oathDeviceProfilesContainer
objectclass: webauthnDeviceProfilesContainer
objectclass: iplanet-am-auth-configuration-service
objectclass: deviceProfilesContainer
boundDevices: { "uuid": "22000176-2d59-4e93-a1c2-c3f0f1437784", "recoveryCodes": [  ], "createdDate": 1777666384389, "lastAccessDate": 1777666384389, "deviceName": "Device Name", "deviceId": "7a5ec9880d3ba928-ef08b3d64603aba0aa52dc249ad6a3df0e698e6d", "key": { "kty": "RSA", "kid": "22000176-2d59-4e93-a1c2-c3f0f1437784", "use": "sig", "alg": "RS512", "n": "8cVkVN_cYKN4Ctpb8BQxD_Q1UGXPZOvMSH7gQeftzzDNTGYgf2p5cvdmfGmQuaoPFEbHFrTgXtGEwC8nherR3N_eLbhBG59eSJgg4_b9SIkTNoWYyX0wqm2iZUx8xol0f9RcWncNnv-g_lugRcozgF40MG7abJRUDLjlCjnjxCD8Cso4ZNjdtkbixxchZ0Go_E0QqW-yKsXC88-RQ5dQPBf-5H4RE50yL-F-Qe5p9AGwqE8eNv9HhLm6WSrWIB4O7uuA8GKKyMJbHnKsZA7ELTCCMxpkkm4PRgSQZER4oGieOam_XO-ApoyIG16d4WrsKRBHjf5kvpb1YBhpYp7Adw", "e": "AQAB" } }
cn: test
inetUserStatus: Active
mail: amadmin
sn: test
uid: test
userPassword: {PBKDF2-HMAC-SHA256}10:Avh5uVkkvXfQ5rgLVNFi0OPrDvWJi1+AcbtFuz5HRL2H5fi8P88O5+ZRHDkXLGzM