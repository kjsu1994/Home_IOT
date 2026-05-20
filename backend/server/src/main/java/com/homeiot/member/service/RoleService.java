package com.homeiot.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RoleService {
    private final Set<String> masterEmails;

    public RoleService(@Value("${app.master-emails:}") String masterEmails) {
        this.masterEmails = parse(masterEmails);
    }

    public List<String> rolesFor(String email) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        roles.add("ROLE_USER");
        if (masterEmails.contains(normalize(email))) {
            roles.add("ROLE_MASTER");
        }
        return List.copyOf(roles);
    }

    public boolean isMasterEmail(String email) {
        return masterEmails.contains(normalize(email));
    }

    private Set<String> parse(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(this::normalize)
                .filter(email -> !email.isBlank())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
