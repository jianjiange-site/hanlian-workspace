package com.aurora.dating.post.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache")
public class PostCacheProperties {

    private String keyPrefix = "hanlian";
    private Duration statDeltaTtl = Duration.ofDays(1);

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getStatDeltaTtl() {
        return statDeltaTtl;
    }

    public void setStatDeltaTtl(Duration statDeltaTtl) {
        this.statDeltaTtl = statDeltaTtl;
    }
}
