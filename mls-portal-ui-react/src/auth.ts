// Sign-in with the tenant's own Keycloak realm: authorization code flow with PKCE, no library, no client secret.

export interface Config {
  authUrl: string; // e.g. https://auth.example.com
  realm: string; // the tenant's realm; its public client has the same name
  itsmApiUrl?: string; // where the ITSM service answers: a path on this site (default /itsm/api/v1) or a full URL of its own
}

const KEY = 'mls.session';

function b64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function random(n: number): string {
  return b64url(crypto.getRandomValues(new Uint8Array(n)));
}

async function challenge(verifier: string): Promise<string> {
  return b64url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))));
}

const base = (c: Config) => `${c.authUrl}/realms/${c.realm}/protocol/openid-connect`;
const redirect = () => `${location.origin}/`;

export async function startLogin(c: Config): Promise<void> {
  const verifier = random(48);
  const state = random(16);
  sessionStorage.setItem('mls.pkce', JSON.stringify({ verifier, state }));
  const q = new URLSearchParams({
    client_id: c.realm, response_type: 'code', scope: 'openid', redirect_uri: redirect(),
    code_challenge: await challenge(verifier), code_challenge_method: 'S256', state,
  });
  location.assign(`${base(c)}/auth?${q}`);
}

/** Finishes a sign-in when the page was opened by Keycloak's redirect; returns the access token or null. */
export async function finishLogin(c: Config): Promise<string | null> {
  const p = new URLSearchParams(location.search);
  const code = p.get('code');
  if (!code) return null;
  const saved = JSON.parse(sessionStorage.getItem('mls.pkce') ?? 'null') as { verifier: string; state: string } | null;
  sessionStorage.removeItem('mls.pkce');
  history.replaceState(null, '', redirect());
  if (!saved || saved.state !== p.get('state')) throw new Error('The sign-in answer did not match the request');
  const res = await fetch(`${base(c)}/token`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'authorization_code', client_id: c.realm, code, redirect_uri: redirect(), code_verifier: saved.verifier }),
  });
  if (!res.ok) throw new Error(`Sign-in failed (${res.status})`);
  const body = (await res.json()) as { access_token: string; expires_in: number };
  sessionStorage.setItem(KEY, JSON.stringify({ token: body.access_token, expires: Date.now() + body.expires_in * 1000 }));
  return body.access_token;
}

export function storedToken(): string | null {
  const s = JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as { token: string; expires: number } | null;
  return s && s.expires > Date.now() + 5000 ? s.token : null;
}

export function logout(c: Config): void {
  sessionStorage.removeItem(KEY);
  location.assign(`${base(c)}/logout?${new URLSearchParams({ client_id: c.realm, post_logout_redirect_uri: redirect() })}`);
}
