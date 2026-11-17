import os
import subprocess
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
CHECK = os.path.join(HERE, "check-platform-render.py")

GOOD = """apiVersion: apps/v1
kind: Deployment
metadata:
  name: svc
spec:
  template:
    spec:
      automountServiceAccountToken: false
      imagePullSecrets:
        - name: harbor-pull
      securityContext:
        runAsNonRoot: true
        seccompProfile: { type: RuntimeDefault }
      containers:
        - name: app
          image: "harbor.uat.mls.internal/mls/svc:1.2.3"
          env:
            - name: PORT
              value: "8080"
          startupProbe: {}
          readinessProbe: {}
          livenessProbe: {}
          resources:
            requests: { cpu: 1m }
            limits: { memory: 1Gi }
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: { drop: [ALL] }
"""


def run(text, app="svc", env="uat"):
    p = subprocess.run([sys.executable, CHECK, app, env], input=text, capture_output=True, text=True)
    return p.returncode, p.stdout


class RenderRules(unittest.TestCase):
    def test_a_compliant_workload_passes(self):
        self.assertEqual(run(GOOD)[0], 0)

    def test_a_writable_root_file_system_is_refused(self):
        code, out = run(GOOD.replace("readOnlyRootFilesystem: true", "readOnlyRootFilesystem: false"))
        self.assertEqual(code, 1)
        self.assertIn("read-only", out)

    def test_running_as_root_is_refused(self):
        code, out = run(GOOD.replace("runAsNonRoot: true", "runAsNonRoot: false"))
        self.assertEqual(code, 1)
        self.assertIn("non-root", out)

    def test_an_image_from_another_registry_is_refused(self):
        code, out = run(GOOD.replace("harbor.uat.mls.internal/mls/svc:1.2.3", "docker.io/library/svc:1.2.3"))
        self.assertEqual(code, 1)
        self.assertIn("not from the uat Harbor", out)

    def test_an_image_from_the_other_environments_harbor_is_refused(self):
        code, _ = run(GOOD.replace("harbor.uat.mls.internal", "harbor.prod.mls.internal"))
        self.assertEqual(code, 1)

    def test_the_latest_tag_is_refused(self):
        code, out = run(GOOD.replace("svc:1.2.3", "svc:latest"))
        self.assertEqual(code, 1)
        self.assertIn("latest", out)

    def test_a_plain_password_is_refused(self):
        code, out = run(GOOD.replace('name: PORT\n              value: "8080"', 'name: DB_PASSWORD\n              value: "x"'))
        self.assertEqual(code, 1)
        self.assertIn("ExternalSecret", out)

    def test_a_missing_probe_is_refused(self):
        code, out = run(GOOD.replace("          livenessProbe: {}\n", ""))
        self.assertEqual(code, 1)
        self.assertIn("liveness", out)

    def test_nothing_rendered_is_refused(self):
        self.assertEqual(run("apiVersion: v1\nkind: Service\n")[0], 1)


if __name__ == "__main__":
    unittest.main()
