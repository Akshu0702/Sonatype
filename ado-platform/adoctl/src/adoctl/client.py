"""A small Azure DevOps REST client: auth from the environment, retries, paging, and a hard rule that the token is never printed.

The token comes only from the environment: ADO_PAT (a personal access token, Basic auth), ADO_TOKEN (a Microsoft Entra access token of a
service identity, Bearer auth; the preferred credential for automation) or SYSTEM_ACCESSTOKEN (a pipeline token, Bearer auth), in that order.
It is held in memory, never written to disk, and scrubbed from every message this module produces.
"""

from __future__ import annotations

import base64
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

API = "api-version=7.1"


class ApiError(Exception):
    def __init__(self, status: int, message: str, url: str = ""):
        super().__init__(f"HTTP {status}: {message}")
        self.status = status
        self.message = message
        self.url = url


def _fail(status: int, payload: bytes, url: str, scrub) -> ApiError:
    """Azure DevOps answers an unaccepted credential with HTTP 203 and an HTML sign-in page (not 401). Treat that as 401."""
    if status == 203:
        return ApiError(401, "the service returned its sign-in page: the credential is not accepted, or has no access to this project", scrub(url))
    msg = payload.decode("utf-8", "replace")[:300] if payload else ""
    try:
        msg = json.loads(msg).get("message", msg)
    except Exception:
        pass
    return ApiError(status, scrub(msg), scrub(url))


class NoToken(Exception):
    pass


def token_from_env(env=None) -> tuple[str, str]:
    """Returns (token, scheme). Scheme is 'Basic' for a PAT and 'Bearer' for an Entra or pipeline token."""
    env = os.environ if env is None else env

    def val(name):  # a pipeline variable that was never set reaches the step as the literal text "$(NAME)"; that is not a token
        v = env.get(name, "")
        return "" if v.startswith("$(") else v

    if val("ADO_PAT"):
        return val("ADO_PAT"), "Basic"
    if val("ADO_TOKEN"):
        return val("ADO_TOKEN"), "Bearer"
    if val("SYSTEM_ACCESSTOKEN"):
        return val("SYSTEM_ACCESSTOKEN"), "Bearer"
    raise NoToken("Set ADO_TOKEN (a service identity's access token), ADO_PAT (a personal access token) or SYSTEM_ACCESSTOKEN (a pipeline token) in the environment.")


def _default_transport(method, url, headers, body, timeout=60):
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, dict(r.headers.items()), r.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers.items()) if e.headers else {}, e.read()


class Client:
    def __init__(self, organisation: str, project: str, token: str, scheme: str = "Basic", transport=None, sleep=time.sleep, retries: int = 3):
        self.organisation = organisation
        self.project = project
        self._token = token
        self._scheme = scheme
        self._transport = transport or _default_transport
        self._sleep = sleep
        self._retries = retries
        self.calls = 0

    # ---- safety -------------------------------------------------------------------------------------------------
    def scrub(self, text) -> str:
        """Removes the token (and its base64 form) from any text. Everything shown to a person goes through this."""
        text = str(text)
        if self._token:
            text = text.replace(self._token, "***")
            text = text.replace(base64.b64encode((":" + self._token).encode()).decode(), "***")
        return text

    def _auth(self) -> str:
        if self._scheme == "Basic":
            return "Basic " + base64.b64encode((":" + self._token).encode()).decode()
        return "Bearer " + self._token

    def __repr__(self):  # never show the token, even in a traceback
        return f"Client({self.organisation}/{self.project})"

    # ---- urls ---------------------------------------------------------------------------------------------------
    def org(self, path: str) -> str:
        return f"https://dev.azure.com/{self.organisation}/_apis/{path}"

    def proj(self, path: str) -> str:
        return f"https://dev.azure.com/{self.organisation}/{urllib.parse.quote(self.project)}/_apis/{path}"

    def vsrm(self, path: str) -> str:
        return f"https://vsrm.dev.azure.com/{self.organisation}/{urllib.parse.quote(self.project)}/_apis/{path}"

    def feeds(self, path: str) -> str:
        return f"https://feeds.dev.azure.com/{self.organisation}/{urllib.parse.quote(self.project)}/_apis/{path}"

    # ---- requests -----------------------------------------------------------------------------------------------
    @staticmethod
    def _with_version(url: str) -> str:
        return url if "api-version=" in url else url + ("&" if "?" in url else "?") + API

    def raw(self, method: str, url: str, body=None, content_type="application/json", extra_headers=None):
        """Returns (status, headers, bytes) with retries on 429, 5xx and dropped connections."""
        url = self._with_version(url)
        headers = {"Authorization": self._auth(), "Accept": "application/json"}
        data = None
        if body is not None:
            data = body if isinstance(body, bytes) else json.dumps(body).encode()
            headers["Content-Type"] = content_type
        headers.update(extra_headers or {})
        attempt = 0
        while True:
            attempt += 1
            self.calls += 1
            try:
                status, rh, payload = self._transport(method, url, headers, data)
            except (urllib.error.URLError, ConnectionError, TimeoutError) as e:
                if attempt > self._retries:
                    raise ApiError(0, self.scrub(f"connection failed: {e}"), url) from None
                self._sleep(min(2 ** attempt, 20))
                continue
            if status == 429 or status >= 500:
                if attempt > self._retries:
                    return status, rh, payload
                wait = rh.get("Retry-After") or rh.get("retry-after")
                self._sleep(min(float(wait), 60) if wait and str(wait).isdigit() else min(2 ** attempt, 20))
                continue
            return status, rh, payload

    def request(self, method: str, url: str, body=None, ok=(200, 201, 204), content_type="application/json", extra_headers=None):
        status, _h, payload = self.raw(method, url, body, content_type, extra_headers)
        if status not in ok:
            raise _fail(status, payload, url, self.scrub)
        if not payload:
            return {}
        try:
            return json.loads(payload)
        except ValueError:
            return {"raw": payload.decode("utf-8", "replace")}

    def get(self, url, **kw):
        return self.request("GET", url, **kw)

    def get_or_none(self, url):
        """GET that turns a 404 into None."""
        try:
            return self.get(url)
        except ApiError as e:
            if e.status == 404:
                return None
            raise

    def get_bytes(self, url, accept="application/zip") -> bytes:
        status, _h, payload = self.raw("GET", url, extra_headers={"Accept": accept})
        if status != 200:
            raise _fail(status, payload, url, self.scrub)
        return payload

    def paged(self, url: str, key: str = "value", page: int = 200):
        """Yields every item of a list endpoint, following x-ms-continuationtoken or $top/$skip."""
        token = None
        skip = 0
        sep = "&" if "?" in url else "?"
        while True:
            u = url
            if token:
                u += f"{sep}continuationToken={urllib.parse.quote(token)}"
            elif "$top=" in url and skip:
                u += f"{sep}$skip={skip}"
            status, h, payload = self.raw("GET", u)
            if status != 200:
                raise _fail(status, payload, u, self.scrub)
            data = json.loads(payload) if payload else {}
            items = data.get(key, data) if isinstance(data, dict) else data
            for i in items:
                yield i
            token = {k.lower(): v for k, v in h.items()}.get("x-ms-continuationtoken")
            if token:
                continue
            if "$top=" in url and len(items) >= page:
                skip += len(items)
                continue
            return
