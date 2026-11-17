# ADR-0012: No Kafka or Redis for now

**Status:** Accepted on 3 November 2026; **amended on 14 November 2026: Kafka is now used** (see the amendment below). Redis is still not used.

## Context
The lab has one application service of its own, the portal, with a few automation functions. Event flows (alarms, workflow results, audit writes from automation) are low in volume and point to point. A managed Kafka cluster needs several brokers and ongoing operation, and a shared cache would add a second stateful service, for no consumer or workload that needs it today. The Baton project uses both, but for nine services exchanging audit events and telemetry.

## Decision
Do not use Kafka (including a managed Kafka service) or Redis (including ElastiCache) in the lab for now. Third-party products that ship their own cache keep it; we do not run a shared Redis for them.

## What we use instead

| Need | Approach |
|---|---|
| Scope lookup cache | In the memory of each calling process, 15 seconds, fails closed ([Authentication and Authorization](/Architecture/Authentication-and-Authorization)) |
| Idempotency keys | A unique constraint in PostgreSQL |
| Rate limiting, provisioning API | API Gateway throttling |
| Rate limiting, portal | An application-level limit on login and upload routes, kept per node. With two nodes the effective limit can be up to twice the setting |
| Alarms to incidents | SNS, a router function, the portal's internal endpoint |
| Workflow waits, retries, approvals | Step Functions |
| Timed work | EventBridge Scheduler |
| Audit events from automation | The portal's internal endpoint, retried by the workflow. A queue (SQS) is an option if events are lost; it is not adopted |
| Log buffering | Logstash persistent queue |

## Consequences
- Fewer stateful services to run, secure, patch and pay for.
- **Harbor uses its own bundled cache.** Harbor's cache is a single instance, so Harbor is highly available at the application tier (two or more replicas, external PostgreSQL and S3) but not at the cache. If the cache is lost, Harbor is restarted; the registry data lives in PostgreSQL and S3. This limit is stated to Sonatype. Argo CD and GitLab also keep their own bundled caches.
- Portal rate limits are approximate across nodes.
- Audit delivery from automation depends on retries; there is no durable buffer.

## Revisit when
- The portal runs on more than two nodes and needs a shared rate limit or cache.
- More services must consume the same events, or an export to a partner system is added.
- Log volume needs a buffer in front of Logstash.
- Harbor's cache outage proves unacceptable, or Sonatype requires cache-level HA.

## Amendment, 14 November 2026: Kafka is used for audit and events

The project owner decided to use **Kafka**. The reason is that the platform is no longer one application service: it is a multi-tenant platform with a service per concern (ADR-0013), and Baton, the reference project, sends audit and events through Kafka with a transactional outbox. MLS follows that pattern.

- **Used for:** audit events (outbox in each service, relayed to Kafka, consumed by the Audit service) and cross-service events. Idempotent consumption with an inbox table (PLATFORM-SPEC-v3 section 8).
- **Still not used:** **Redis.** Rate limiting, scope and feature caches stay as designed (in-process caches with a short time to live, per-node limits, API Gateway throttling) until a shared store is justified.
- **Open:** where Kafka runs (Amazon MSK or self-managed on the shared hosts), its size, TLS and access control, and its cost line in the bill of materials and the total cost of ownership. These are infrastructure work items.
- The table above ("What we use instead") remains correct for everything except audit delivery and cross-service events.
