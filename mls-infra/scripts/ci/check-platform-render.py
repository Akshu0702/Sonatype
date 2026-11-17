#!/usr/bin/env python3
"""Reads rendered Kubernetes YAML from standard input and checks the rules every platform workload must meet.
Usage: helm template ... | check-platform-render.py <app> <env>
Needs no libraries: the rendered output is simple enough to read with a small parser, so this runs on any build agent."""
import re
import sys


def documents(text):
    return [d for d in re.split(r"(?m)^---\s*$", text) if d.strip()]


def main():
    app, env = sys.argv[1], sys.argv[2]
    text = sys.stdin.read()
    problems = []
    kinds = [m.group(1) for d in documents(text) for m in [re.search(r"(?m)^kind:\s*(\S+)", d)] if m]

    if "Deployment" not in kinds:
        problems.append("no Deployment rendered")
    else:
        dep = next(d for d in documents(text) if re.search(r"(?m)^kind:\s*Deployment", d))
        for needle, why in (
            ("runAsNonRoot: true", "the pod must run as non-root"),
            ("readOnlyRootFilesystem: true", "the root file system must be read-only"),
            ("allowPrivilegeEscalation: false", "privilege escalation must be off"),
            ("drop:", "capabilities must be dropped"),
            ("seccompProfile:", "a seccomp profile must be set"),
            ("limits:", "a memory limit must be set"),
            ("requests:", "resource requests must be set"),
            ("startupProbe:", "a startup probe is required"),
            ("readinessProbe:", "a readiness probe is required"),
            ("livenessProbe:", "a liveness probe is required"),
            ("imagePullSecrets:", "the image comes from Harbor, so a pull secret is required"),
            ("automountServiceAccountToken: false", "the service account token must not be mounted"),
        ):
            if needle not in dep:
                problems.append(why)
        image = re.search(r"(?m)^\s*image:\s*\"?([^\"\s]+)", dep)
        if not image:
            problems.append("no image")
        else:
            ref = image.group(1)
            if f"harbor.{env}.mls.internal/" not in ref:
                problems.append(f"image {ref} is not from the {env} Harbor")
            if ":latest" in ref:
                problems.append("the latest tag is not allowed")

    if "env" in text and re.search(r"(?mi)^\s*-?\s*name:\s*\S*(PASSWORD|SECRET|TOKEN)\S*\s*\n\s*value:", text):
        problems.append("a password, secret or token is set as a plain value; it must come from an ExternalSecret")

    if problems:
        print(f"{app} ({env}): " + "; ".join(problems))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
