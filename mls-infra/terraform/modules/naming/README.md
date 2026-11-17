# naming

Builds resource names and the standard tags from the naming standard, so no name or tag is typed by hand.
Standard: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Engineering/Repositories-and-Naming

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test`). Not yet used by any environment.**

```
<project>-<resource-type>-<environment>-<function>-<id>
   mls   -     ec2       -    dev       -   web    - 001
```

## Rules enforced

- Lower case letters, digits and single hyphens only; the name is rejected, never truncated, if it is longer than `max_length`.
- `environment` is `dev`, `qa`, `uat` or `prod`; `function` is one word from the agreed list; `id` is three digits.
- `owner` is a team or role. A person or an email address is rejected.
- Region and the long name are tags, not name parts. The standard tags always win over `extra_tags`.

## Use

```hcl
module "name_web" {
  source        = "../../modules/naming"
  resource_type = "ec2"
  environment   = "dev"
  function      = "web"
  owner         = "platform"
  region        = "ap-south-1"
}

# module.name_web.name            -> "mls-ec2-dev-web-001"
# module.name_web.tags_with_name  -> standard tags plus Name
```

## Test

```
terraform init -backend=false
terraform test
```

## Open points

- Subnets, route tables and gateways that exist once per availability zone extend the VPC name with the tier and the zone letter instead of an id. This needs recording in the standard.
