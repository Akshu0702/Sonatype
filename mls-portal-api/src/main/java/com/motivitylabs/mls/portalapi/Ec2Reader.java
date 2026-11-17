package com.motivitylabs.mls.portalapi;

import com.motivitylabs.mls.portalapi.AwsEnvironments.Instance;
import com.motivitylabs.mls.portalapi.AwsEnvironments.Tagged;
import com.motivitylabs.mls.portalapi.AwsEnvironments.Vpc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads the lab's VPCs and instances from EC2 with read-only calls (DescribeVpcs, DescribeInstances) in one region. Credentials come
 * from the standard AWS chain (an instance role in AWS, a read-only profile on a laptop); nothing is configured here.
 */
@Component
public class Ec2Reader implements AwsEnvironments.Reader {

    private final Ec2Client ec2;
    private final String region;
    private final Filter project;

    public Ec2Reader(@Value("${mls.aws.region:ap-south-1}") String region, @Value("${mls.aws.project-tag:mls}") String projectTag) {
        this.region = region;
        this.ec2 = Ec2Client.builder().region(Region.of(region)).build();
        this.project = Filter.builder().name("tag:project").values(projectTag).build();
    }

    @Override
    public String region() {
        return region;
    }

    @Override
    public List<Tagged<Vpc>> vpcs() {
        return ec2.describeVpcsPaginator(r -> r.filters(project)).vpcs().stream().map(v -> {
            Map<String, String> t = tags(v.tags());
            return new Tagged<>(t.get("environment"), new Vpc(v.vpcId(), t.getOrDefault("Name", v.vpcId()), v.cidrBlock(), t.get("function")));
        }).toList();
    }

    @Override
    public List<Tagged<Instance>> instances() {
        return ec2.describeInstancesPaginator(r -> r.filters(project)).reservations().stream().flatMap(r -> r.instances().stream()).map(i -> {
            Map<String, String> t = tags(i.tags());
            return new Tagged<>(t.get("environment"), new Instance(i.instanceId(), t.getOrDefault("Name", i.instanceId()), i.instanceTypeAsString(),
                    i.state().nameAsString(), i.privateIpAddress(), i.placement().availabilityZone(), t.get("function"), String.valueOf(i.launchTime())));
        }).toList();
    }

    private static Map<String, String> tags(List<Tag> tags) {
        return tags.stream().collect(Collectors.toMap(Tag::key, Tag::value, (a, b) -> a));
    }
}
