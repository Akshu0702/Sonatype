# vpn-site-to-site

Site-to-Site VPN connections to the Transit Gateway for Sonatype and Motivity: a customer gateway and a VPN connection with two tunnels per site, IKEv2, AES-256, SHA2-256, DH group 14 and BGP.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Architecture/Infrastructure-Diagrams

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS.**

## Use

```hcl
module "vpn" {
  source             = "../../modules/vpn-site-to-site"
  name               = module.name_vpn.name
  tags               = module.name_vpn.tags
  transit_gateway_id = module.tgw.transit_gateway_id

  connections = {
    sonatype = { customer_gateway_ip = "<public address of Sonatype's VPN device>", bgp_asn = 65010 }
    motivity = { customer_gateway_ip = "<public address of Motivity's VPN device>", bgp_asn = 65020 }
  }
}
```

Pass `module.vpn.transit_gateway_attachment_ids` to the transit-gateway module's `external_attachments`.

## What you need from each site

- The public address of its VPN device and its BGP ASN.
- Office ranges that do **not** overlap `10.40.0.0/13` (primary) or `10.48.0.0/13` (DR).

## Handing over the configuration

Give the site's network team the two AWS endpoint addresses (`tunnel_addresses`) and the device configuration file, which you download from the console or the CLI. **The pre-shared keys that AWS generates are stored in Terraform state**, so the state bucket must stay private and encrypted (see `tf-state-backend`). This module does not output the keys or the configuration.

## Alarms

The module outputs `tunnel_metrics`: the `VpnId` and `TunnelIpAddress` of every tunnel. The alert catalogue's VPN alerts (INF-01 both tunnels down, INF-02 one down) are defined in the `cloudwatch-alarms` module, not here.

## Test

```
terraform init -backend=false
terraform test
```
