package com.betteruc.client;

import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterUCAuthClientTest {

    @Test
    void permitsMinecraftVerificationOnlyWhileFullyDisconnected() {
        assertTrue(BetterUCAuthClient.isAuthenticationSafeState(false, false, false, false, false, false));
        assertFalse(BetterUCAuthClient.isAuthenticationSafeState(true, false, false, false, false, false));
        assertFalse(BetterUCAuthClient.isAuthenticationSafeState(false, true, false, false, false, false));
        assertFalse(BetterUCAuthClient.isAuthenticationSafeState(false, false, true, false, false, false));
        assertFalse(BetterUCAuthClient.isAuthenticationSafeState(false, false, false, true, false, false));
        assertFalse(BetterUCAuthClient.isAuthenticationSafeState(false, false, false, false, true, false));
        assertFalse(BetterUCAuthClient.isAuthenticationSafeState(false, false, false, false, false, true));
    }

    @Test
    void distinguishesInvalidMinecraftCredentialsFromTransientNetworkFailures() {
        assertTrue(BetterUCAuthClient.isCredentialFailure(
                new CompletionException(new IllegalStateException("Access token invalid or revoked."))
        ));
        assertTrue(BetterUCAuthClient.isCredentialFailure(new IllegalStateException("Invalid Session")));
        assertFalse(BetterUCAuthClient.isCredentialFailure(new HttpTimeoutException("request timed out")));
    }

    @Test
    void transientRetryBackoffIsBounded() {
        assertEquals(15_000L, BetterUCAuthClient.retryDelayMs(1));
        assertEquals(30_000L, BetterUCAuthClient.retryDelayMs(2));
        assertEquals(60_000L, BetterUCAuthClient.retryDelayMs(3));
        assertEquals(300_000L, BetterUCAuthClient.retryDelayMs(20));
    }
}
