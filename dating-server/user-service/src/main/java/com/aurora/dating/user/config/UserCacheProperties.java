package com.aurora.dating.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "app.cache")
public class UserCacheProperties {

    private String keyPrefix = "hanlian";
    private Duration banStatusTtl = Duration.ofMinutes(5);

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getBanStatusTtl() {
        return banStatusTtl;
    }

    public void setBanStatusTtl(Duration banStatusTtl) {
        this.banStatusTtl = banStatusTtl;
    }

    private Duration profileTtl = Duration.ofHours(24);

    public Duration getProfileTtl() {
        return profileTtl;
    }

    public void setProfileTtl(Duration profileTtl) {
        this.profileTtl = profileTtl;
    }
}
