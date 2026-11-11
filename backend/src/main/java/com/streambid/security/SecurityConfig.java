package com.streambid.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/catalog/streamers", "/api/catalog/streamers/{id}", "/api/catalog/streamers/{id}/photo",
                        "/api/catalog/campaigns", "/api/catalog/campaigns/{id}").permitAll()
                .anyRequest().authenticated());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        http.requestCache(cache -> cache.disable());
        http.formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .successHandler((request, response, auth) -> response.setStatus(204))
                .failureHandler((request, response, error) -> response.setStatus(401))
                .permitAll());
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
