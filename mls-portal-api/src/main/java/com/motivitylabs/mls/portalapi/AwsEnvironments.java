package com.motivitylabs.mls.portalapi;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Groups what AWS says exists (tagged {@code project=mls}) into environments by the {@code environment} tag. It never invents
 * anything: no tag, no environment. The answer is kept for a short while so the console cannot hammer the AWS API.
 */
@Service
public class AwsEnvironments {

    public record Vpc(String id, String name, String cidr, String function) {
    }

    public record Instance(String id, String name, String type, String state, String privateIp, String zone, String function, String launched) {
    }

    public record Environment(String name, List<Vpc> vpcs, List<Instance> instances, int running) {
    }

    public record Overview(String source, String region, String observedAt, List<Environment> environments) {
    }

    /** A thing AWS returned, with the value of its {@code environment} tag (null when it has none). */
    public record Tagged<T>(String environment, T item) {
    }

    public interface Reader {
        String region();

        List<Tagged<Vpc>> vpcs();

        List<Tagged<Instance>> instances();
    }

    private static final Duration KEEP = Duration.ofSeconds(60);

    private final Reader reader;
    private final Clock clock;
    private volatile Overview last;
    private volatile Instant lastAt = Instant.EPOCH;

    public AwsEnvironments(Reader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    public Overview overview() {
        Instant now = clock.instant();
        Overview kept = last;
        if (kept != null && Duration.between(lastAt, now).compareTo(KEEP) < 0) {
            return kept;
        }
        synchronized (this) {
            if (last == null || Duration.between(lastAt, now).compareTo(KEEP) >= 0) {
                last = group(reader.region(), reader.vpcs(), reader.instances(), now);
                lastAt = now;
            }
            return last;
        }
    }

    static Overview group(String region, List<Tagged<Vpc>> vpcs, List<Tagged<Instance>> instances, Instant now) {
        Map<String, List<Vpc>> v = new TreeMap<>();
        Map<String, List<Instance>> i = new TreeMap<>();
        vpcs.stream().filter(t -> t.environment() != null).forEach(t -> v.computeIfAbsent(t.environment(), k -> new ArrayList<>()).add(t.item()));
        instances.stream().filter(t -> t.environment() != null).forEach(t -> i.computeIfAbsent(t.environment(), k -> new ArrayList<>()).add(t.item()));
        List<String> names = new ArrayList<>(v.keySet());
        i.keySet().stream().filter(k -> !v.containsKey(k)).forEach(names::add);
        names.sort(Comparator.naturalOrder());
        List<Environment> out = new ArrayList<>();
        for (String name : names) {
            List<Instance> inst = i.getOrDefault(name, List.of()).stream().sorted(Comparator.comparing(Instance::name)).toList();
            out.add(new Environment(name, v.getOrDefault(name, List.of()), inst, (int) inst.stream().filter(x -> "running".equals(x.state())).count()));
        }
        return new Overview("aws", region, now.toString(), out);
    }
}
