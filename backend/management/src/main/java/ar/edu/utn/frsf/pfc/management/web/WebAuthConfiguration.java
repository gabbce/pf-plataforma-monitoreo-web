package ar.edu.utn.frsf.pfc.management.web;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebAuthConfiguration.Settings.class)
class WebAuthConfiguration {

    @Bean
    Clock webClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder webPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @ConfigurationProperties("pfc.web")
    record Settings(Duration sessionTtl, Duration csrfTtl) {
        Settings {
            if (sessionTtl.isZero() || sessionTtl.isNegative() || csrfTtl.isZero() || csrfTtl.isNegative()) {
                throw new IllegalArgumentException("Web session and CSRF lifetimes must be positive");
            }
        }
    }
}
