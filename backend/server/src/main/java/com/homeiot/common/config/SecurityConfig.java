package com.homeiot.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final DeviceApiKeyFilter deviceApiKeyFilter;

    public SecurityConfig(DeviceApiKeyFilter deviceApiKeyFilter) {
        this.deviceApiKeyFilter = deviceApiKeyFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/state/report").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/telemetry").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/ota/events").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/ota/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/devices/*/commands/pending").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/devices/*/commands/*").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(deviceApiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(@Value("${app.admin.username}") String username,
                                                 @Value("${app.admin.password}") String password,
                                                 PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
