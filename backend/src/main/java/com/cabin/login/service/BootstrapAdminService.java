package com.cabin.login.service;

import com.cabin.config.BootstrapAdminProperties;
import com.cabin.login.entity.AppUser;
import com.cabin.login.mapper.AppUserMapper;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapAdminService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);

    private final BootstrapAdminProperties properties;
    private final ObjectProvider<AppUserMapper> appUserMapperProvider;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminService(
            BootstrapAdminProperties properties,
            ObjectProvider<AppUserMapper> appUserMapperProvider,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.appUserMapperProvider = appUserMapperProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppUserMapper mapper = appUserMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        if (isBlank(properties.username()) || isBlank(properties.password())) {
            log.warn("BOOTSTRAP_ADMIN_USERNAME or BOOTSTRAP_ADMIN_PASSWORD is not set; admin bootstrap skipped");
            return;
        }

        String username = properties.username().trim().toLowerCase(Locale.ROOT);
        if (mapper.countByUsername(username) > 0) {
            return;
        }

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setEmail(blankToNull(properties.email()));
        user.setRoleCode("ADMIN");
        user.setStatus("ACTIVE");
        mapper.insert(user);
        log.info("Bootstrap admin created: {}", username);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
