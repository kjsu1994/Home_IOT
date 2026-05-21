package com.homeiot.common.config;

import com.homeiot.auth.filter.JwtAuthenticationFilter;
import com.homeiot.auth.oauth2.CustomOAuth2UserService;
import com.homeiot.auth.oauth2.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final DeviceApiKeyFilter deviceApiKeyFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    public SecurityConfig(DeviceApiKeyFilter deviceApiKeyFilter,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          CustomOAuth2UserService customOAuth2UserService,
                          OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.deviceApiKeyFilter = deviceApiKeyFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"login required\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/reissue", "/api/auth/password-reset/**").permitAll()
                        .requestMatchers("/api/members", "/api/members/**").permitAll()
                        .requestMatchers("/api/oauth2/**", "/api/login/oauth2/**", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/state/report").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/telemetry").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/ota/events").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/ota/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/camera/frame").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/camera/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/devices/*/camera/ws").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/devices/*/camera/control").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/devices/*/commands/pending").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/devices/*/commands/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cameras/*/mjpeg").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cameras/*/ws").permitAll()
                        .requestMatchers("/api/**").hasAnyRole("USER", "MASTER")
                        .anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/oauth2/authorization"))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler))
                .addFilterBefore(deviceApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
