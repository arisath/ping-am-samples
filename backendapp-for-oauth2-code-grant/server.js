const express = require('express');
const session = require('express-session');
const app = express();

const CONFIG = {
    AM_URL: "http://localhost:8080/am/oauth2/realms/root",
    CLIENT_ID: "nodejs-backend-app",
    CLIENT_SECRET: "password",
    REDIRECT_URI: "http://localhost:3001/callback",
    SCOPE: "openid profile"
};

app.use(session({
    secret: 'dev-secret-shhh',
    resave: false,
    saveUninitialized: true,
    cookie: { secure: false }
}));

// --- 1. The Landing Page (The Button) ---
app.get('/', (req, res) => {
    res.send(`
        <html>
            <body style="font-family: sans-serif; padding: 2rem; text-align: center;">
                <h1>My Secure Node.js App</h1>
                <p>Status: <strong>Not Logged In</strong></p>
                <hr>
                <a href="/login">
                    <button style="padding: 10px 20px; cursor: pointer; background: #007bff; color: white; border: none; border-radius: 4px;">
                        Log In with ForgeRock AM
                    </button>
                </a>
            </body>
        </html>
    `);
});

// --- 2. The Login Trigger (The Redirect) ---
app.get('/login', (req, res) => {
    const authUrl = new URL(`${CONFIG.AM_URL}/authorize`);
    authUrl.searchParams.append("response_type", "code");
    authUrl.searchParams.append("client_id", CONFIG.CLIENT_ID);
    authUrl.searchParams.append("redirect_uri", CONFIG.REDIRECT_URI);
    authUrl.searchParams.append("scope", CONFIG.SCOPE);
    authUrl.searchParams.append("state", "xyz789");

    res.redirect(authUrl.toString());
});

// --- 3. The Callback (Token Exchange) ---
app.get('/callback', async (req, res) => {
    const { code } = req.query;
    if (!code) return res.send("Authorization failed.");

    try {
        const credentials = Buffer.from(`${CONFIG.CLIENT_ID}:${CONFIG.CLIENT_SECRET}`).toString('base64');
        const response = await fetch(`${CONFIG.AM_URL}/access_token`, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "Authorization": `Basic ${credentials}` // Credentials moved to header
            },
            body: new URLSearchParams({
                grant_type: "authorization_code",
                code: code,
                redirect_uri: CONFIG.REDIRECT_URI
                // client_id and client_secret are NO LONGER in the body
            })
        });

        const tokens = await response.json();
        req.session.tokens = tokens;
        res.redirect('/profile');

    } catch (err) {
        res.status(500).send("Error exchanging token: " + err.message);
    }
});

// --- 4. The Profile (Protected Data) ---
app.get('/profile', (req, res) => {
    if (!req.session.tokens) return res.redirect('/');

    res.send(`
        <body style="font-family: sans-serif; padding: 2rem;">
            <h1>Welcome Back!</h1>
            <p>You have successfully authenticated via the Code Grant.</p>
            <pre style="background: #f4f4f4; padding: 10px;">${JSON.stringify(req.session.tokens, null, 2)}</pre>
            <a href="/logout">Logout</a>
        </body>
    `);
});

app.get('/logout', (req, res) => {
    req.session.destroy();
    res.redirect('/');
});

app.listen(3001, () => console.log('App running at http://localhost:3001'));