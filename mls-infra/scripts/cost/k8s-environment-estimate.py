#!/usr/bin/env python3
"""A planning estimate of the monthly cost of the UAT and Prod Kubernetes environments (terraform/live/uat and prod).

THESE ARE ESTIMATES FOR PLANNING ONLY. The unit prices below are approximate on-demand prices for ap-south-1 in US dollars, written
from general knowledge of AWS price lists, NOT read from the price list (the read-only account cannot read it). Check each one against
https://aws.amazon.com/pricing/ before anyone relies on a figure; edit PRICES and run the script again. Data transfer, NAT data
processing and support plans are not included. Nothing for UAT or Prod exists in AWS: no money is being spent on them.

Usage: python3 k8s-environment-estimate.py [--margin 0.25]
"""
import argparse

HOURS = 730  # hours in an average month

PRICES = {  # USD, approximate
    "eks_control_plane_per_hour": 0.10,
    "m6i.large_per_hour": 0.101,
    "m6i.xlarge_per_hour": 0.202,
    "nat_gateway_per_hour": 0.056,
    "alb_per_hour": 0.0225,
    "alb_lcu_per_month": 6.0,
    "db.m6g.large_per_hour": 0.178,
    "db.m6g.xlarge_per_hour": 0.356,
    "rds_gp3_per_gb_month": 0.131,
    "ebs_gp3_per_gb_month": 0.0912,
    "s3_per_gb_month": 0.025,
    "kms_key_per_month": 1.0,
    "secret_per_month": 0.40,
    "control_plane_logs_per_month": 10.0,
}

ENVIRONMENTS = {
    "uat": dict(nodes=("m6i.large", 2), nats=2, db=("db.m6g.large", False, 100), pvc_gb=62, node_disk_gb=100, registry_gb=100, logs_factor=1.0),
    "prod": dict(nodes=("m6i.xlarge", 3), nats=3, db=("db.m6g.xlarge", True, 200), pvc_gb=62, node_disk_gb=100, registry_gb=300, logs_factor=2.0),
}
KEYS, SECRETS = 5, 9


def estimate(env):
    p, e = PRICES, ENVIRONMENTS[env]
    itype, count = e["nodes"]
    dbclass, multi_az, dbgb = e["db"]
    az = 2 if multi_az else 1
    lines = [
        ("EKS control plane", p["eks_control_plane_per_hour"] * HOURS),
        (f"{count} x {itype} nodes (on demand)", p[f"{itype}_per_hour"] * count * HOURS),
        (f"{e['nats']} NAT gateways (hourly only)", p["nat_gateway_per_hour"] * e["nats"] * HOURS),
        (f"RDS {dbclass}{' Multi-AZ' if multi_az else ''}", p[f"{dbclass}_per_hour"] * az * HOURS),
        (f"RDS storage {dbgb} GB x {az}", p["rds_gp3_per_gb_month"] * dbgb * az),
        ("Application load balancer", p["alb_per_hour"] * HOURS + p["alb_lcu_per_month"]),
        (f"Node disks {count} x {e['node_disk_gb']} GB", p["ebs_gp3_per_gb_month"] * count * e["node_disk_gb"]),
        (f"Persistent volumes {e['pvc_gb']} GB", p["ebs_gp3_per_gb_month"] * e["pvc_gb"]),
        (f"Registry images in S3 {e['registry_gb']} GB", p["s3_per_gb_month"] * e["registry_gb"]),
        (f"{KEYS} KMS keys", p["kms_key_per_month"] * KEYS),
        (f"{SECRETS} secrets", p["secret_per_month"] * SECRETS),
        ("Control plane logs", p["control_plane_logs_per_month"] * e["logs_factor"]),
    ]
    return lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--margin", type=float, default=0.25, help="the plus-or-minus range shown around the total (default 25%%)")
    a = ap.parse_args()
    for env in ENVIRONMENTS:
        lines = estimate(env)
        total = sum(v for _, v in lines)
        print(f"\n{env.upper()}  (estimate for planning only; nothing exists in AWS)")
        for name, value in lines:
            print(f"  {name:<44} {value:>9,.0f}")
        print(f"  {'Total per month (USD)':<44} {total:>9,.0f}   range {total * (1 - a.margin):,.0f} to {total * (1 + a.margin):,.0f}")


if __name__ == "__main__":
    main()
