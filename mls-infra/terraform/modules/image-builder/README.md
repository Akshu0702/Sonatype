# image-builder

An EC2 Image Builder pipeline for the hardened image (ADR-0003): the AWS-managed Ubuntu 24.04 LTS image, then `ansible/playbooks/harden.yml` of this repository (SSH, kernel network settings, unused modules and packages, time, updates, auditd), validated and tested, scanned by Amazon Inspector, and kept in this account and region as `<ami_name_prefix>-<build date>`.

The playbooks reach the build as an archive in the `mls-*` deploy bucket under `images/` (`tar -czf ansible-1.0.0.tar.gz ansible`). A component version cannot be changed once created: change the playbooks, then raise `component_version` and `recipe_version` and the archive name.

The pipeline runs when someone starts it (output `start_build_command`) or on `schedule_expression`.

**Needs:** a build subnet with outbound HTTPS (Image Builder gives the instance a public address only if the subnet assigns one on launch), Amazon Inspector activated once if `scan_images` is true, and the IAM additions in `terraform/live/lab-test/provisioning/iam-by-hand.md`.

**Not tested:** the pipeline has never run. The tests assert the configuration (encrypted gp3, not shared, scanned, IMDSv2, least-privilege build role); only a build proves the component works on a real instance.

Tests: `terraform test` (mocked provider; nothing is built).
