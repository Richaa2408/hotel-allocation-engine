package com.hotel.engine.seed;

import com.hotel.engine.model.Enums;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * stayflow.seed.* in application.properties: whether to load the demo hotel into an empty database,
 * the random seed, and the demo staff logins.
 */
@ConfigurationProperties("stayflow.seed")
public record SeedProperties(boolean enabled, long seed, List<DemoUser> users) {

    public record DemoUser(String username, String password, String displayName, Enums.Role role) {
    }

    public SeedProperties {
        users = users == null ? List.of() : users;
    }
}
