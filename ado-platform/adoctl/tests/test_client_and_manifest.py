import base64
import json

import fake_ado
import pytest

from adoctl.client import ApiError, Client, NoToken, token_from_env
from adoctl.manifest import Manifest, ManifestError


# ---- token sources ----------------------------------------------------------------------------------------------------
def test_token_sources_and_schemes():
    assert token_from_env({"ADO_PAT": "p"}) == ("p", "Basic")
    assert token_from_env({"ADO_TOKEN": "t"}) == ("t", "Bearer")
    assert token_from_env({"SYSTEM_ACCESSTOKEN": "s"}) == ("s", "Bearer")
    assert token_from_env({"ADO_PAT": "p", "ADO_TOKEN": "t", "SYSTEM_ACCESSTOKEN": "s"}) == ("p", "Basic")
    assert token_from_env({"ADO_TOKEN": "t", "SYSTEM_ACCESSTOKEN": "s"}) == ("t", "Bearer")  # the service identity wins over the pipeline token
    with pytest.raises(NoToken):
        token_from_env({})
    # an unset pipeline variable arrives as the literal text "$(ADO_TOKEN)": not a token
    assert token_from_env({"ADO_TOKEN": "$(ADO_TOKEN)", "SYSTEM_ACCESSTOKEN": "s"}) == ("s", "Bearer")
    with pytest.raises(NoToken):
        token_from_env({"ADO_TOKEN": "$(ADO_TOKEN)"})


def test_ado_token_is_sent_as_bearer_and_pat_as_basic():
    seen = []

    def t(method, url, headers, body):
        seen.append(headers["Authorization"])
        return 200, {}, b"{}"

    Client("o", "p", "ENTRA-TOKEN-VALUE", "Bearer", transport=t).get("https://x/_apis/a")
    Client("o", "p", "pat-value", "Basic", transport=t).get("https://x/_apis/a")
    assert seen[0] == "Bearer ENTRA-TOKEN-VALUE"
    assert seen[1] == "Basic " + base64.b64encode(b":pat-value").decode()


def test_token_is_scrubbed_from_errors_repr_and_urls():
    def t(method, url, headers, body):
        return 403, {}, json.dumps({"message": f"bad credential {headers['Authorization']} for {url}"}).encode()

    c = Client("o", "p", "SECRET-ENTRA-TOKEN", "Bearer", transport=t)
    with pytest.raises(ApiError) as e:
        c.get("https://x/_apis/a")
    assert "SECRET-ENTRA-TOKEN" not in str(e.value) and "SECRET-ENTRA-TOKEN" not in e.value.url
    assert "SECRET-ENTRA-TOKEN" not in repr(c) and "SECRET-ENTRA-TOKEN" not in c.scrub("x SECRET-ENTRA-TOKEN y")
    c2 = Client("o", "p", "tok-1234", "Basic", transport=t)
    assert "tok-1234" not in c2.scrub("Basic " + base64.b64encode(b":tok-1234").decode())


# ---- retries and paging -----------------------------------------------------------------------------------------------
def test_retries_on_429_and_5xx_then_succeeds():
    calls, sleeps = [], []

    def t(method, url, headers, body):
        calls.append(1)
        if len(calls) == 1:
            return 429, {"Retry-After": "2"}, b""
        if len(calls) == 2:
            return 503, {}, b""
        return 200, {}, b'{"ok": true}'

    c = Client("o", "p", "x", transport=t, sleep=sleeps.append)
    assert c.get("https://x/_apis/a") == {"ok": True}
    assert len(calls) == 3 and sleeps[0] == 2


def test_the_sign_in_page_answer_for_a_bad_credential_is_an_unauthorised_error():
    c = Client("o", "p", "x", transport=lambda *a: (203, {}, b"<!DOCTYPE html>sign in"), sleep=lambda s: None)
    for call in (lambda: c.get("https://x/_apis/a"), lambda: list(c.paged("https://x/_apis/a")), lambda: c.get_bytes("https://x/_apis/a")):
        with pytest.raises(ApiError) as e:
            call()
        assert e.value.status == 401 and "sign-in" in e.value.message


def test_gives_up_after_retries():
    c = Client("o", "p", "x", transport=lambda *a: (500, {}, b'{"message":"boom"}'), sleep=lambda s: None, retries=2)
    with pytest.raises(ApiError) as e:
        c.get("https://x/_apis/a")
    assert e.value.status == 500


def test_paging_follows_continuation_token_and_skip():
    pages = [(["a", "b"], "tok1"), (["c"], None)]
    seen = []

    def t(method, url, headers, body):
        seen.append(url)
        items, nxt = pages[len(seen) - 1]
        return 200, ({"x-ms-continuationtoken": nxt} if nxt else {}), json.dumps({"value": items}).encode()

    c = Client("o", "p", "x", transport=t)
    assert list(c.paged("https://x/_apis/a")) == ["a", "b", "c"]
    assert "continuationToken=tok1" in seen[1]

    n = []

    def t2(method, url, headers, body):
        n.append(url)
        items = ["1", "2"] if len(n) == 1 else ["3"]
        return 200, {}, json.dumps({"value": items}).encode()

    assert list(Client("o", "p", "x", transport=t2).paged("https://x/_apis/a?$top=2", page=2)) == ["1", "2", "3"]
    assert "$skip=2" in n[1]


# ---- manifest ---------------------------------------------------------------------------------------------------------
def test_missing_manifest_is_refused(tmp_path):
    with pytest.raises(ManifestError, match="No manifest"):
        Manifest.load(str(tmp_path / "nope.yml"))


def test_unfilled_placeholders_are_refused(tmp_path):
    p = tmp_path / "m.yml"
    p.write_text(fake_ado.MANIFEST.replace("Lead One", "<delivery-lead>"), encoding="utf-8")
    with pytest.raises(ManifestError, match="placeholder"):
        Manifest.load(str(p))


def test_token_shaped_value_in_manifest_is_refused(tmp_path):
    p = tmp_path / "m.yml"
    p.write_text(fake_ado.MANIFEST.replace("Pm One", "A" * 48), encoding="utf-8")
    with pytest.raises(ManifestError, match="token"):
        Manifest.load(str(p))


def test_other_project_is_refused(manifest):
    manifest.require_project("Proj")
    with pytest.raises(ManifestError, match="not the project in the manifest"):
        manifest.require_project("Some-Other-Project")


def test_sprints_are_counted_from_the_start(manifest):
    s = manifest.sprint_dates()
    assert [x[0] for x in s] == ["Sprint 1", "Sprint 2"] and str(s[0][1]) == "2026-10-26" and str(s[1][1]) == "2026-11-02"
