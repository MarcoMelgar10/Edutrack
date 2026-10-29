package com.edutrack;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot configuration root.
 *
 * <p>It lives in the {@code com.edutrack} root package on purpose: component scanning, JPA entity
 * scanning and Spring Data repository scanning then cover every module (application wiring,
 * infrastructure adapters, desktop UI) without extra {@code @EnableJpaRepositories}/{@code @EntityScan}.
 *
 * <p>There is no {@code main} here; the desktop entry point is {@link com.edutrack.desktop.Launcher}.
 */
@SpringBootApplication
public class EduTrackApplication {
}
