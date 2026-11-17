"""Tests for plan-guard.py. Run: python3 scripts/ci/test_plan_guard.py"""
import importlib.util
import json
import os
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("plan_guard", os.path.join(os.path.dirname(__file__), "plan-guard.py"))
pg = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pg)


def plan(changes, region="ap-south-1", via_var=False, extra_providers=None):
    cfg = {"aws": {"name": "aws", "expressions": ({"region": {"references": ["var.region"]}} if via_var else {"region": {"constant_value": region}})}}
    cfg.update(extra_providers or {})
    return {
        "variables": {"region": {"value": region}},
        "configuration": {"provider_config": cfg},
        "resource_changes": [{"address": a, "mode": "managed", "change": {"actions": acts, **extra}} for a, acts, extra in changes],
    }


class PlanGuard(unittest.TestCase):
    def run_guard(self, p, *args):
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
            json.dump(p, f)
        try:
            return pg.main([f.name, *args])
        finally:
            os.unlink(f.name)

    def test_create_only_is_accepted(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["create"], {}), ("aws_subnet.b", ["create"], {})]), "--expect-region", "ap-south-1"), 0)

    def test_no_op_and_read_are_accepted(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["no-op"], {}), ("data.aws_x.y", ["read"], {})])), 0)

    def test_update_is_refused_by_default(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["update"], {})])), 1)

    def test_delete_is_refused_by_default(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["delete"], {})])), 1)

    def test_replace_is_refused_even_with_create_allowed(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["delete", "create"], {})]), "--allow", "create"), 1)

    def test_replace_needs_both_actions_allowed(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["delete", "create"], {})]), "--allow", "create,delete"), 0)

    def test_update_allowed_when_asked(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["update"], {})]), "--allow", "create,update"), 0)

    def test_destroy_plan_with_delete_allowed(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["delete"], {})]), "--allow", "delete"), 0)

    def test_import_is_always_refused(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["no-op"], {"importing": {"id": "vpc-123"}})]), "--allow", "create,update,delete"), 1)

    def test_wrong_region_is_refused(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["create"], {})], region="us-east-1"), "--expect-region", "ap-south-1"), 1)

    def test_region_from_variable_is_resolved(self):
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["create"], {})], via_var=True), "--expect-region", "ap-south-1"), 0)
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["create"], {})], region="us-east-1", via_var=True), "--expect-region", "ap-south-1"), 1)

    def test_aliased_provider_in_another_region_is_refused(self):
        dr = {"aws.dr": {"name": "aws", "alias": "dr", "expressions": {"region": {"constant_value": "us-east-1"}}}}
        self.assertEqual(self.run_guard(plan([("aws_vpc.a", ["create"], {})], extra_providers=dr), "--expect-region", "ap-south-1"), 1)

    def test_unpinned_region_is_refused(self):
        p = plan([("aws_vpc.a", ["create"], {})])
        p["configuration"]["provider_config"]["aws"]["expressions"] = {}
        self.assertEqual(self.run_guard(p, "--expect-region", "ap-south-1"), 1)

    def test_unknown_allow_value_is_an_error(self):
        self.assertEqual(self.run_guard(plan([]), "--allow", "create,destroy"), 2)


if __name__ == "__main__":
    unittest.main()
