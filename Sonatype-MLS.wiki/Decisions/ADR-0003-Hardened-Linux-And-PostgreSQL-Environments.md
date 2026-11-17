# ADR-0003: Provisioned environments use hardened Linux and PostgreSQL

**Status:** Accepted (distribution and PostgreSQL versions to be confirmed)

## Context
Sonatype needs on-demand test environments by operating system and database.

## Decision
Environments run on hardened Linux images built by EC2 Image Builder, with PostgreSQL pinned per configuration at launch. Every environment carries TTL, owner and ticket tags and is removed by a reaper.

## Consequences
- Repeatable, scanned images; no unmanaged long-lived test hosts.
- Image versions and retention need an owner.
- Whether PostgreSQL runs on the instance or on RDS is chosen per configuration.
