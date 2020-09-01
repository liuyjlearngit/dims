package com.cmdi.dims.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;

import com.cmdi.dims.util.SecurityUtil;

public class AuditorAwareImpl implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.ofNullable(SecurityUtil.getUsername());
    }
}
