// Fills the ${...} placeholders of a state machine file with test values, the way the Terraform module `provisioning` does
// (templatestring). Used only by the local tests; Terraform does the real rendering.
//
// Usage: node render.mjs <asl-file> > rendered.json
import fs from "node:fs";

const CATALOGUE = {
  "ubuntu24-pg16": {
    amiId: "ami-0123456789abcdef0",
    instanceType: "t3.small",
    rootVolumeGb: 30,
    userData: "#!/bin/bash\nset -eu\napt-get install -y postgresql-16 >/dev/null 2>&1 && echo a&b\n",
  },
};

// A value written into a JSONata expression that itself sits inside a JSON string: encode twice, drop the outer quotes.
// Terraform's jsonencode writes the characters < > & as backslash-u escapes, so this does too and the two renderings are identical.
export function embed(value) {
  const inner = JSON.stringify(value).replace(/[<>&]/g, (c) => "\\u" + c.charCodeAt(0).toString(16).padStart(4, "0"));
  const twice = JSON.stringify(inner);
  return twice.slice(1, -1);
}

export const VALUES = {
  configurations_json: embed(CATALOGUE),
  subnet_ids_json: embed(["subnet-0aaaaaaaaaaaaaaa1", "subnet-0bbbbbbbbbbbbbbb2"]),
  security_group_id: "sg-0123456789abcdef0",
  instance_profile_name: "mls-iprof-dev-lab-001",
  associate_public_ip: "false",
  environment: "dev",
  default_ttl_hours: "8",
  max_ttl_hours: "72",
};

export function render(text) {
  let out = text;
  for (const [k, v] of Object.entries(VALUES)) out = out.split("${" + k + "}").join(v);
  const left = out.indexOf("${");
  if (left >= 0) throw new Error("unfilled placeholder: " + out.slice(left, left + 40));
  JSON.parse(out); // must still be valid JSON
  return out;
}

if (process.argv[1] && process.argv[1].endsWith("render.mjs")) {
  process.stdout.write(render(fs.readFileSync(process.argv[2], "utf8")));
}
