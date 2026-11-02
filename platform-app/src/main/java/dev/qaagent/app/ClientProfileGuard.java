package dev.qaagent.app;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The client profile is a reserved placeholder. Starting with it fails immediately
 * so a later environment cannot be mistaken for a working deployment.
 */
@Component
@Profile("client")
public class ClientProfileGuard {

    public ClientProfileGuard() {
        throw new IllegalStateException("The client profile is a placeholder and is disabled.");
    }
}
