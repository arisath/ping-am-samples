<p align="center">
  <a href="https://github.com/ForgeRock/sdk-sample-apps">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Ping Identity Logo">
  </a>
  <hr/>
</p>

# NovaPay — Ping Identity Android SDK Quickstart

A sample Android banking app demonstrating the Ping Identity (ForgeRock) Android SDK. Covers device binding, transaction signing with custom claims, and OAuth 2.0 login — both embedded and browser-based.

---

## What it demonstrates

| Feature | Description |
|---|---|
| Embedded login | `FRUser.login()` with SDK-handled node callbacks |
| Centralized login | Browser-based OAuth 2.0 via AppAuth + Chrome Custom Tabs |
| Device binding | Registers a device key pair with AM; PIN-protected private key |
| Transaction signing | Signs payment details (amount, recipient) as custom JWT claims using the bound device key |
| WebAuthn | Registration and authentication callbacks |
| Session tokens | Displays access token, refresh token, and ID token |

---

## Prerequisites

- Android Studio Hedgehog or later
- Android device or emulator (API 23+)
- Docker (for running AM locally)
- Java 17+

---

## Running AM locally

The Docker setup bundles AM, OpenDJ, and Amster configuration into a single container.

```bash
cd Docker
docker build -t novapay-am .
docker run -p 8080:8080 novapay-am
```

AM will be available at `http://localhost:8080/am`. The emulator accesses it via `http://10.0.2.2:8080/am`.

First startup takes ~2 minutes while Amster applies the realm configuration.

### AM configuration

| Setting | Value |
|---|---|
| Realm | `android` |
| Server URL | `http://10.0.2.2:8080/am` |
| OAuth Client ID | `AndroidTest` |
| Redirect URI | `org.forgerock.demo:/oauth2redirect` |
| Cookie | `iPlanetDirectoryPro` |

### Authentication trees

| Tree | Purpose |
|---|---|
| `Login` | Username + password login |
| `DeviceBinding` | Authenticates user then binds the device (generates key pair, PIN set by user) |
| `TransactionSigning` | Verifies device signature over payment claims |

---

## Building the app

Two build flavors control the login mode:

| Flavor | Login mode |
|---|---|
| `central` | Browser-based OAuth via AppAuth |
| `embedded` | SDK-handled login with in-app UI |

Select the variant in Android Studio via **Build > Select Build Variant**, or build from the command line:

```bash
# Embedded login, debug
./gradlew assembleEmbeddedDebug

# Centralized login, debug
./gradlew assembleCentralDebug
```

---

## App flow

### 1. Device binding (first time)

Tap **Bind Device** → AM's `DeviceBinding` tree runs:
1. Username + password collected
2. `DeviceBindingNode` generates an RSA key pair on the device
3. User sets a PIN to protect the private key
4. Public key registered with AM; private key stored in an encrypted local keystore

Binding state is persisted in `SharedPreferences` (`is_bound`).

### 2. Transaction signing

Tap **Sign Transaction** → payment form appears:
1. User enters recipient and amount
2. Summary shown: *"YOU ARE SIGNING: Pay £X to Y"*
3. User confirms → AM's `TransactionSigning` tree runs
4. `DeviceSigningVerifierNode` sends a challenge (nonce) to the app
5. SDK builds a JWT containing the challenge + custom claims:
   ```json
   {
     "sub": "user123",
     "challenge": "<nonce from AM>",
     "amount": "50.00",
     "currency": "GBP",
     "recipient": "John Smith",
     "timestamp": 1234567890
   }
   ```
6. User enters PIN → JWT signed with RS512 using device private key
7. Signed JWT sent to AM → signature verified against stored public key

> **Security note:** Custom claims are self-reported by the app. A production implementation should have the backend create the transaction record first and pass it to AM, so the challenge is derived from server-side data rather than client-supplied values. See the discussion on Option A vs Option B transaction signing in the codebase.

### 3. Login

- **Embedded**: SDK handles all node callbacks in-app (username/password dialog, WebAuthn, IdP)
- **Centralized**: Launches browser for OAuth 2.0 authorization code flow with PKCE

### 4. Reset binding (testing)

Tap **Reset Binding (Test)** (visible when device is bound) to clear the binding and start over. This removes the `is_bound` flag from SharedPreferences.

---

## Project structure

```
quickstart/
└── src/main/java/com/forgerock/kotlinapp/
    ├── MainActivity.kt          # Entry point; handles all AM node callbacks
    ├── UserInfoFragment.kt      # Post-login screen showing tokens
    ├── PaymentDialogFragment.kt # Payment details form before transaction signing
    ├── NodeDialogFragment.kt    # Generic dialog for username/password/choice nodes
    ├── BrandedPinFragment.kt    # Custom PIN entry dialog (replaces SDK default)
    └── BrandedPinCollector.kt   # PinCollector impl wiring BrandedPinFragment into SDK
```

---

## SDK version

ForgeRock Android Auth SDK `4.8.0`

Key SDK classes used:

| Class | Purpose |
|---|---|
| `FRUser` | Login, logout, access token |
| `FRSession` | Tree-based authentication (device binding, transaction signing) |
| `DeviceBindingCallback` | Generates key pair and registers device |
| `DeviceSigningVerifierCallback` | Signs challenge + custom claims with device key |
| `ApplicationPinDeviceAuthenticator` | PIN-based key protection (pluggable via `PinCollector`) |

---

## Customising the PIN screen

The app replaces the SDK's default PIN dialog with a branded one. To customise further, edit:

- `res/layout/fragment_branded_pin.xml` — layout
- `BrandedPinFragment.kt` — behaviour
- `BrandedPinCollector.kt` — wired into `bind()` and `sign()` via `brandedDeviceAuthenticator()`

---

## Requirements

- Android Studio Hedgehog or later
- Ping Advanced Identity Cloud or Ping AM 7.1+
- Android API level 23+
