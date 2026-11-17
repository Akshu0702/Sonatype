# Site-to-Site VPN

How the offices reach the lab, and what we need from the customer to set it up.

[[_TOC_]]

**Status: design.** Nothing is built. The Terraform modules `transit-gateway` and `vpn-site-to-site` exist as draft pull requests ([Terraform Modules](/Infrastructure/Terraform-Modules)). The customer gateway and VPN connection are created only after the customer returns the details below.

## Design

One **Transit Gateway** in Mumbai (ap-south-1) is the hub. Each VPC attaches to it, and each office connects to it with its own Site-to-Site VPN connection (two tunnels, BGP preferred).

| Attachment | CIDR | Purpose |
|---|---|---|
| Shared services VPC | `10.40.0.0/20` | Portal, Keycloak, IQ Server, ELK, CI/CD. The hub for everything users and automation need |
| Lab Env-1 VPC | `10.41.0.0/20` | Current-release testing |
| Lab Env-2 VPC | `10.42.0.0/20` | Upgrade and version matrix |
| VPN: Sonatype office | Customer's range | Sonatype users |
| VPN: Motivity office | Motivity's range | Motivity engineers |

The CIDRs are the plan in [Infrastructure Diagrams](/Architecture/Infrastructure-Diagrams). They are proposals until the customer confirms there is no overlap.

**Routing rules** (separate Transit Gateway route tables):

- Offices reach the shared services VPC only (portal, Keycloak, Kibana over HTTPS).
- Offices do not reach Env-1 or Env-2 directly.
- Env-1 and Env-2 cannot reach each other.
- Shared services reaches Env-1 and Env-2 for automation only.

Region is ap-south-1 only. Nothing in us-east-1 is touched.

## What we need from the customer

The customer-facing questionnaire is sent as a Word document ("Site-to-Site VPN questionnaire"). One copy is completed per office. The same items are tracked here.

| # | Item | Why we need it | Status |
|---|---|---|---|
| 1 | VPN device make, model and software version | Selects the configuration template AWS generates | Open |
| 2 | Device public IPv4 address (both, if a pair) | Becomes the customer gateway | Open |
| 3 | Is the device behind NAT? Public IP of the NAT | NAT traversal must be enabled | Open |
| 4 | Single device or high-availability pair | Decides how many tunnels are used | Open |
| 5 | Static routes or BGP | BGP gives automatic failover | Open |
| 6 | BGP ASN of the customer device | Needed for BGP peering | Open |
| 7 | Every on-premises CIDR that must reach the lab | Routes on both sides | Open |
| 8 | Confirmation that none overlaps `10.40.0.0/20`, `10.41.0.0/20`, `10.42.0.0/20` | An overlap needs NAT or a new range | Open |
| 9 | Traffic direction, services and ports needed | Keeps security groups and network ACLs narrow | Open |
| 10 | IKE version, encryption, integrity and Diffie-Hellman group the customer's policy requires | Must be inside what AWS supports | Open |
| 11 | Required tunnel lifetimes, if fixed | Phase 1 and phase 2 settings | Open |
| 12 | Who generates the pre-shared keys | Default: AWS generates them and we share them through a secure channel | Open |
| 13 | DNS: names to resolve in either direction, DNS server IPs | Decides whether Route 53 Resolver endpoints are needed | Open |
| 14 | Change window, device administrator contact, firewall lead time | Scheduling | Open |
| 15 | Test host IP and a named tester on the customer side | Acceptance test | Open |

## What we supply to the customer

- The AWS tunnel endpoint IPs (assigned when the VPN connection is created).
- The pre-shared keys, through a secure channel and never by email.
- The AWS-side ASN (default `64512`), the lab CIDRs and the tunnel inside CIDRs.
- The AWS-generated device configuration file for their device.

## Proposed parameters

| Setting | Value |
|---|---|
| IKE | IKEv2 |
| Encryption | AES-256 |
| Integrity | SHA-2 (256 or higher) |
| Diffie-Hellman | Group 14 or higher |
| Routing | BGP, ASN `64512` on the AWS side |
| Tunnels | Two per connection, in different AWS endpoints |
| Monitoring | CloudWatch tunnel-state alarms, routed to the incident process |

If the customer's policy needs something outside these, record it as a risk on the delivery board before building.

## Order of work

1. Customer returns the questionnaire (items 1 to 15). **Blocks the VPN only**; the Transit Gateway and VPCs do not wait.
2. Build the Transit Gateway, route tables and VPC attachments (no customer details needed).
3. Create the customer gateway and VPN connection; download the device configuration.
4. Customer configures their device in the agreed window.
5. Test: tunnels up, BGP routes learned, portal reachable from the test host, Env-1 and Env-2 not reachable from the office.
6. Record the result and the evidence on the delivery ticket.

## Not covered yet

- No customer details have been received.
- Throughput and failover have not been tested.
- DR (ap-south-2) pre-provisioning of the Transit Gateway and VPN is in the [Bill of Materials](/Architecture/Bill-of-Materials) but not designed here.
