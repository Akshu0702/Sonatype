# baseline_hardening

A hardening baseline for Linux hosts: SSH, kernel network settings, unused modules and packages, time synchronisation, automatic security updates, file permissions, banner, umask and a persistent journal.

Supports the Debian family (Ubuntu 22.04, 24.04) and the Red Hat family (Amazon Linux 2023). The distribution for the lab is not yet confirmed.

## What it sets

| Area | Settings |
|---|---|
| SSH | No root login, no passwords, no empty passwords, no forwarding, verbose logging, modern ciphers, MACs and key exchange, a banner, optional `AllowGroups` |
| Network | No redirects or source routing, log impossible addresses, reverse-path filter, SYN cookies, IP forwarding off (on for container hosts with `baseline_hardening_allow_ip_forwarding`) |
| Modules | Unused filesystems and protocols cannot be loaded |
| Packages | Legacy clients and servers removed |
| Time | Chrony against the Amazon Time Sync Service |
| Updates | Security updates applied automatically |
| Files | Permissions on sensitive files, umask 027, core dumps off, legal banner |
| Logs | Persistent journal |

## Variables

All variables start with `baseline_hardening_`. See `defaults/main.yml`. The switches `manage_services`, `apply_sysctl` and `validate_sshd` exist for hosts without an init system (container tests).

## Notes

- This is a baseline aligned with the CIS Level 1 server topics. It is not a certified or complete implementation of a CIS benchmark.
- SSH access is through Systems Manager; the role removes password logins, so make sure key or Session Manager access works before applying it.
