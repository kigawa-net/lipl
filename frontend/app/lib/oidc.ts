const KEYCLOAK_BASE_URL = "https://user.kigawa.net";
const REALM = "lipl";
const CLIENT_ID = "lipl-frontend";

const ISSUER = `${KEYCLOAK_BASE_URL}/realms/${REALM}`;
const AUTHORIZATION_ENDPOINT = `${ISSUER}/protocol/openid-connect/auth`;
const TOKEN_ENDPOINT = `${ISSUER}/protocol/openid-connect/token`;
const END_SESSION_ENDPOINT = `${ISSUER}/protocol/openid-connect/logout`;

const CODE_VERIFIER_KEY = "lipl.oidc.code_verifier";
const ACCESS_TOKEN_KEY = "lipl.oidc.access_token";
const REFRESH_TOKEN_KEY = "lipl.oidc.refresh_token";

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function generateCodeVerifier(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}

function redirectUri(): string {
  return `${window.location.origin}/callback`;
}

export async function redirectToLogin(): Promise<void> {
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = await generateCodeChallenge(codeVerifier);
  sessionStorage.setItem(CODE_VERIFIER_KEY, codeVerifier);

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: redirectUri(),
    response_type: "code",
    scope: "openid",
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
  });

  window.location.assign(`${AUTHORIZATION_ENDPOINT}?${params.toString()}`);
}

export async function handleCallback(code: string): Promise<void> {
  const codeVerifier = sessionStorage.getItem(CODE_VERIFIER_KEY);
  if (!codeVerifier) {
    throw new Error("code_verifierが見つかりません。再度ログインしてください");
  }

  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: CLIENT_ID,
    redirect_uri: redirectUri(),
    code,
    code_verifier: codeVerifier,
  });

  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!response.ok) {
    throw new Error(`トークン取得に失敗しました（${response.status}）`);
  }

  const tokens = (await response.json()) as { access_token: string; refresh_token?: string };
  sessionStorage.setItem(ACCESS_TOKEN_KEY, tokens.access_token);
  if (tokens.refresh_token) {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refresh_token);
  }
  sessionStorage.removeItem(CODE_VERIFIER_KEY);
}

export function getAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY);
}

// Keycloakのaccess tokenは短命（lipl realmでは5分）なため、APIが401を返した際に
// refresh tokenで再取得する。refresh tokenが無い/失効している場合はnullを返し、
// 呼び出し側で再ログインへ誘導する。
export async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = sessionStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) {
    return null;
  }

  const body = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: CLIENT_ID,
    refresh_token: refreshToken,
  });

  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!response.ok) {
    clearTokens();
    return null;
  }

  const tokens = (await response.json()) as { access_token: string; refresh_token?: string };
  sessionStorage.setItem(ACCESS_TOKEN_KEY, tokens.access_token);
  if (tokens.refresh_token) {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refresh_token);
  }
  return tokens.access_token;
}

export function isAuthenticated(): boolean {
  return getAccessToken() !== null;
}

export function clearTokens(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function redirectToLogout(): void {
  const logoutParams = new URLSearchParams({
    client_id: CLIENT_ID,
    post_logout_redirect_uri: window.location.origin,
  });
  clearTokens();
  window.location.assign(`${END_SESSION_ENDPOINT}?${logoutParams.toString()}`);
}
