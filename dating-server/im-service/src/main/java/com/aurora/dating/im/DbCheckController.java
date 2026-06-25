package com.aurora.dating.im;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DbCheckController {

    private final JdbcTemplate jdbcTemplate;

    public DbCheckController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/internal/check/db")
    public Map<String, String> checkDb() {
        Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
        return Map.of(
                "database", "ok",
                "result", String.valueOf(result)
        );
    }
}
