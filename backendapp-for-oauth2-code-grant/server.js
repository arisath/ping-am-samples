const express = require('express');
const session = require('express-session');
const app = express();

// ── Provider selection ─────────────────────────────────────────────────────
// Set PROVIDER=spring (or ping) before starting:  PROVIDER=spring node server.js
const PROVIDER = process.env.PROVIDER || 'ping';

const PROVIDERS = {
    ping: {
        authorizeUrl: "http://localhost:8080/am/oauth2/realms/root/authorize",
        tokenUrl:     "http://localhost:8080/am/oauth2/realms/root/access_token",
    },
    spring: {
        authorizeUrl: "http://localhost:9000/oauth2/authorize",
        tokenUrl:     "http://localhost:9000/oauth2/token",
    }
};

if (!PROVIDERS[PROVIDER]) {
    console.error(`Unknown PROVIDER "${PROVIDER}". Use "ping" or "spring".`);
    process.exit(1);
}

const CONFIG = {
    ...PROVIDERS[PROVIDER],
    CLIENT_ID:    "nodejs-backend-app",
    CLIENT_SECRET: "password",
    REDIRECT_URI: "http://localhost:3001/callback",
    SCOPE:        "openid profile"
};

console.log(`Using provider: ${PROVIDER}  (${CONFIG.authorizeUrl})`);

// ── Helpers ────────────────────────────────────────────────────────────────
function decodeIdToken(token) {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        return JSON.parse(Buffer.from(parts[1], 'base64').toString('utf-8'));
    } catch (e) {
        console.error("JWT Decode Error:", e);
        return null;
    }
}

// ── Middleware ─────────────────────────────────────────────────────────────
app.use(session({
    secret: 'dev-secret-shhh',
    resave: false,
    saveUninitialized: true,
    cookie: { secure: false }
}));

// ── Routes ─────────────────────────────────────────────────────────────────
app.get('/', (req, res) => {
    const badge = PROVIDER === 'spring'
        ? `<span style="background:#e6f4ea;color:#137333;padding:2px 8px;border-radius:4px;font-size:.8rem;font-weight:600">Spring Auth Server :9000</span>`
        : `<span style="background:#e8f4fd;color:#1a73e8;padding:2px 8px;border-radius:4px;font-size:.8rem;font-weight:600">Ping AM :8080</span>`;

    res.send(`
        <body style="font-family: sans-serif; padding: 2rem; text-align: center;">
            <h1>Node.js Backend — Auth Code Flow</h1>
            <p>IdP: ${badge}</p>
            <a href="/login"><button style="padding: 10px 20px; cursor: pointer;">Log In</button></a>
        </body>
    `);
});

app.get('/login', (req, res) => {
    const authUrl = new URL(CONFIG.authorizeUrl);
    authUrl.searchParams.append("response_type", "code");
    authUrl.searchParams.append("client_id", CONFIG.CLIENT_ID);
    authUrl.searchParams.append("redirect_uri", CONFIG.REDIRECT_URI);
    authUrl.searchParams.append("scope", CONFIG.SCOPE);
    authUrl.searchParams.append("state", "xyz789");
    res.redirect(authUrl.toString());
});

app.get('/callback', async (req, res) => {
    const { code } = req.query;
    if (!code) return res.send("Authorization failed.");

    try {
        const credentials = Buffer.from(`${CONFIG.CLIENT_ID}:${CONFIG.CLIENT_SECRET}`).toString('base64');
        const response = await fetch(CONFIG.tokenUrl, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "Authorization": `Basic ${credentials}`
            },
            body: new URLSearchParams({
                grant_type:   "authorization_code",
                code:         code,
                redirect_uri: CONFIG.REDIRECT_URI
            })
        });

        const tokens = await response.json();

        if (tokens.id_token) {
            tokens.decoded_id_token = decodeIdToken(tokens.id_token);
        }

        req.session.tokens = tokens;
        res.redirect('/profile');

    } catch (err) {
        res.status(500).send("Error exchanging token: " + err.message);
    }
});

app.get('/profile', (req, res) => {
    if (!req.session.tokens) return res.redirect('/');

    const { tokens } = req.session;
    const rawTokens = { ...tokens };
    delete rawTokens.decoded_id_token;

    res.send(`
        <body style="font-family: sans-serif; padding: 2rem;">
            <h1>User Session Details</h1>

            <h3>Decoded ID Token (OIDC Claims)</h3>
            <pre style="background: #e9ecef; padding: 15px; border-radius: 5px;">${JSON.stringify(tokens.decoded_id_token, null, 2)}</pre>

            <h3>Raw Token Response</h3>
            <pre style="background: #f8f9fa; padding: 15px; border-radius: 5px; color: #666;">${JSON.stringify(rawTokens, null, 2)}</pre>

            <hr>
            <a href="/logout">Logout</a>
        </body>
    `);
});

app.get('/logout', (req, res) => {
    req.session.destroy();
    res.redirect('/');
});

app.listen(3001, () => console.log('App running at http://localhost:3001'));
