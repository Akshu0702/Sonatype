import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

import fake_ado  # noqa: E402
from adoctl.checks import run_all  # noqa: E402
from adoctl.client import Client  # noqa: E402
from adoctl.manifest import Manifest  # noqa: E402


@pytest.fixture
def mpath(tmp_path):
    p = tmp_path / "ado-project.yml"
    p.write_text(fake_ado.MANIFEST, encoding="utf-8")
    return str(p)


@pytest.fixture
def manifest(mpath):
    return Manifest.load(mpath)


@pytest.fixture
def fake():
    return fake_ado.fresh()


@pytest.fixture
def client(fake):
    return Client(fake_ado.ORG, fake_ado.PROJ, fake_ado.TOKEN, "Basic", transport=fake, sleep=lambda s: None)


@pytest.fixture
def audit(fake, client, manifest):
    def go():
        return run_all(client, manifest)

    return go


def by(results, id, subject=None):
    return [r for r in results if r.id == id and (subject is None or r.subject == subject)]
