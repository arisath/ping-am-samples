# ping-am-samples

A collection of sample applications demonstrating OAuth 2.0 and OIDC integration with Ping Identity Access Manager (AM).

## Repository Structure

```
ping-am-samples/
├── Docker/                               # Containerised Ping AM environment (Tomcat + OpenDJ)
│   ├── Dockerfile
│   ├── startup.sh
│   ├── ds-install.sh
│   └── amster-config/                    # Amster automation: OAuth clients, CORS, realm config
├── ping-websdk/                          # Embedded login SPA using ForgeRock JavaScript SDK
├── spa-for-oauth2-pkce/                  # Public client SPA — Authorization Code + PKCE
├── spa-for-oauth2-implicit-flow/         # Legacy SPA — Implicit flow (educational only)
├── backendapp-for-oauth2-code-grant/     # Confidential client — Authorization Code (Node.js)
└── android-sdk/                          # Placeholder (not yet implemented)
```

## Components

### Docker — Ping AM Container

Builds a self-contained AM instance with an embedded OpenDJ directory server. Amster runs on first boot to configure the OAuth 2.0 provider, CORS policy, and pre-registered clients.

- AM available at: `http://localhost:8080/am`
- Default admin credentials: `admin / password` (**dev only**)

### ping-websdk — ForgeRock JavaScript SDK

Webpack + TypeScript application that uses the `@forgerock/javascript-sdk` for browser-based authentication via OIDC. The dev server runs on `https://localhost:8443`.

Configuration is loaded from a `.env` file:

| Variable | Default | Description |
|---|---|---|
| `SERVER_URL` | `http://localhost:8080/am` | AM base URL |
| `REALM_PATH` | `test` | AM realm |
| `SCOPE` | `openid profile` | Requested OIDC scopes |
| `TREE` | `sdkUsernamePasswordJourney` | Authentication journey |
| `WEB_OAUTH_CLIENT` | `websdk` | Registered OAuth client ID |
| `TIMEOUT` | `3000` | SDK timeout (ms) |

### spa-for-oauth2-pkce — PKCE Single-Page App

Vanilla JS SPA demonstrating the current best practice for public browser-based clients. No server required.

```mermaid
sequenceDiagram
participant User
participant SPA as Browser (Your App)
participant AM as Ping AM

    note over SPA: 1. Start Flow (login())
    User->>SPA: Click "Log In"
    SPA->>SPA: Create random String: 'state' (CSRF)
    SPA->>SPA: Create random String: 'code_verifier'
    SPA->>SPA: Hash verifier: SHA-256 ('code_challenge')
    SPA->>SPA: Store in SessionStorage:<br>'pkce_state', 'pkce_verifier'
    SPA->>AM: Redirect (GET /authorize)<br>Includes: code_challenge, code_challenge_method=S256, state...

    note over AM: 2. Authentication
    User->>AM: Direct Login (AM Console)
    AM-->>User: (MFA Challenge/Success)

    note over AM: 3. Authorization Grant
    AM->>SPA: Redirect back (Callback URI)<br>Includes: code=xyz123, state=abc

    note over SPA: 4. The Exchange (handleCallback())
    SPA->>SPA: Get 'code' & 'state' from URL
    SPA->>SPA: Validate: URL state == stored 'pkce_state'
    SPA->>SPA: Retrieve stored 'pkce_verifier'
    SPA->>SPA: Clean URL (replaceState)

    note over SPA, AM: 5. Back-channel Token Request
    SPA->>AM: Direct POST /access_token<br>Includes: grant_type=authorization_code, code, code_verifier...
    note over AM: Validate:<br>1. Code is valid<br>2. HASH(code_verifier) == code_challenge from Step 1

    AM-->>SPA: Success: Return Tokens (JSON)<br>Includes: id_token, access_token

    note over SPA: 6. Completion
    SPA->>SPA: decodeJwt(id_token) to display claims
    SPA->>User: Display "Authenticated" UI
```

### spa-for-oauth2-implicit-flow — Implicit Flow SPA (Legacy)

Demonstrates the deprecated Implicit grant type where tokens are delivered directly via the URL fragment. Included for educational and legacy-migration reference only — **do not use in new applications**.

```mermaid
sequenceDiagram
    participant User
    participant SPA as Browser (Implicit App)
    participant AM as Ping AM

    note over SPA: 1. Start Flow (login())
    User->>SPA: Click "Log In (Implicit)"
    SPA->>AM: Redirect (GET /authorize)<br>Includes: response_type=token id_token, nonce=xyz123...

    note over AM: 2. Authentication
    User->>AM: Direct Login (AM Console)
    AM-->>User: Success (Session Created)

    note over AM: 3. Token Delivery (Front-Channel)
    AM->>SPA: Redirect back (Callback URI)<br>Includes: #access_token=...&id_token=...
    note right of AM: Tokens are appended to the URL fragment (#)

    note over SPA: 4. The Extraction (handleCallback())
    SPA->>SPA: Read tokens from window.location.hash
    SPA->>SPA: Clean URL (replaceState) to hide tokens

    note over SPA: 5. Display
    SPA->>SPA: parseJwt(id_token)
    SPA->>User: Show "Authenticated" UI & Decoded Claims
```

### backendapp-for-oauth2-code-grant — Confidential Client (Node.js)

Express.js server-side application demonstrating the Authorization Code flow for confidential clients. The token exchange happens server-to-server using HTTP Basic Auth (`client_id:client_secret`). Tokens are stored in server-side sessions and never exposed to the browser.

```mermaid
sequenceDiagram
    participant User
    participant Browser
    participant NodeApp as Node.js Backend
    participant AM as Ping AM

    note over NodeApp: 1. Initiation
    User->>Browser: Click "Log In"
    Browser->>NodeApp: GET /login
    NodeApp-->>Browser: 302 Redirect to AM<br/>(client_id, response_type=code)

    note over AM: 2. Authentication
    Browser->>AM: GET /authorize
    User->>AM: Enter Credentials
    AM-->>User: Success (Session Cookie)

    note over AM: 3. Delivery of Authorization Code
    AM-->>Browser: 302 Redirect to /callback?code=xyz...
    Browser->>NodeApp: GET /callback?code=xyz...

    note over NodeApp, AM: 4. The Exchange (Back-Channel)
    NodeApp->>NodeApp: Create Basic Auth Header<br/>(base64(ID:Secret))
    NodeApp->>AM: POST /access_token<br/>(code + Basic Auth)
    note right of NodeApp: Server-to-server, secret never exposed

    AM-->>NodeApp: Returns JSON (access_token, id_token)

    note over NodeApp: 5. Session Establishment
    NodeApp->>NodeApp: decodeIdToken()
    NodeApp->>NodeApp: Store tokens in req.session
    NodeApp-->>Browser: 302 Redirect to /profile

    note over Browser: 6. Secure View
    Browser->>NodeApp: GET /profile
    NodeApp-->>Browser: Render HTML with Decoded Claims
```

## Getting Started

### Prerequisites

- Docker
- Node.js 18+ and npm (for `ping-websdk` and `backendapp-for-oauth2-code-grant`)

### 1. Start Ping AM

```bash
cd Docker
docker build -t ping-am-sample .
docker run -p 8080:8080 ping-am-sample
```

AM will be available at `http://localhost:8080/am` once startup completes (~2 minutes).

### 2. Run the WebSDK sample

```bash
cd ping-websdk
npm install
npm run dev
```

Open `https://localhost:8443` in your browser.

### 3. Run the backend Authorization Code sample

```bash
cd backendapp-for-oauth2-code-grant
npm install
node server.js
```

App available at `http://localhost:3001`.

### 4. Serve the PKCE or Implicit SPA

```bash
cd spa-for-oauth2-pkce
npx serve .
```

Open `http://localhost:3000` in your browser.

## Port Reference

| Component | URL |
|---|---|
| Ping AM | `http://localhost:8080/am` |
| WebSDK dev server | `https://localhost:8443` |
| Backend (code grant) | `http://localhost:3001` |
| SPAs (via `serve`) | `http://localhost:3000` |

## Security Notes

- Default credentials (`admin/password`) and pre-shared secrets are for local development only.
- The Implicit flow sample is provided for educational purposes — it is deprecated by [RFC 9700](https://datatracker.ietf.org/doc/html/rfc9700). Use PKCE for new browser-based applications.
- In production, use HTTPS, rotate secrets, and store tokens server-side.
