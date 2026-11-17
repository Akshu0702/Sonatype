# Provisioning API

Request, inspect, extend and tear down on-demand environments. The portal, the command line and test automation all use this one API.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built.** Base path `/v1`, behind a private API Gateway. Contract: `mls-automation-api/openapi/provisioning.v1.yaml`.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/configurations` | The priority configurations (operating system image, database engine and version) |
| POST | `/environments` | Request an environment. Requires `configuration`, `ttl`, `ticket` and `owner` |
| GET | `/environments` | List environments |
| GET | `/environments/{id}` | Status, endpoints, expiry |
| POST | `/environments/{id}/extend` | Extend the TTL, within the maximum, with a ticket |
| DELETE | `/environments/{id}` | Tear down now |
| GET | `/environments/{id}/evidence` | Teardown and creation evidence |

## Rules

- A request without a TTL, owner or ticket is rejected. No tag, no launch.
- The default TTL is 8 hours and the maximum is 72 hours; longer needs a new ticket.
- The caller must hold the scope `environments.request`, checked through the portal's internal scope lookup (cached for 15 seconds, fails closed).
- A request is idempotent on its `Idempotency-Key`.
- Quotas are enforced; a request over quota returns a clear problem response and raises an alarm.

## Command line

```
mls env create --config <configuration> --ttl 8h --ticket <number>
mls env list
mls env show <id>
mls env extend <id> --ttl 4h --ticket <number>
mls env delete <id>
```

## Related

[API Reference](/API-Reference), [Step Functions](/Services/Step-Functions), [Terraform Modules](/Infrastructure/Terraform-Modules).
