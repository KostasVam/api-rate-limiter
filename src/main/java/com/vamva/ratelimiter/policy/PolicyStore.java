package com.vamva.ratelimiter.policy;

import com.vamva.ratelimiter.model.Policy;

import java.util.List;

/**
 * SPI for pluggable policy sources.
 *
 * <p>The default implementation ({@link YamlPolicyStore}) reads policies from Spring
 * YAML configuration. Consumers can implement this interface to load policies from
 * other sources (database, API, config server, etc.).</p>
 *
 * <p>To use a custom store, declare a {@code PolicyStore} bean in your application
 * context — the auto-configuration will pick it up via {@code @ConditionalOnMissingBean}.</p>
 *
 * <p>Example:</p>
 * <pre>
 * &#64;Bean
 * public PolicyStore databasePolicyStore(PolicyRepository repo) {
 *     return new DatabasePolicyStore(repo);
 * }
 * </pre>
 */
public interface PolicyStore {

    /**
     * Returns the current list of active policies.
     *
     * <p>This method may be called on every request (via {@link PolicyResolver}),
     * so implementations should cache or pre-load policies rather than querying
     * a remote source synchronously on each call.</p>
     *
     * @return unmodifiable list of active policies
     */
    List<Policy> getPolicies();

    /**
     * Reloads policies from the underlying source.
     *
     * <p>Called by the actuator reload endpoint. Implementations should refresh
     * their internal cache from the source of truth.</p>
     */
    void reload();
}
