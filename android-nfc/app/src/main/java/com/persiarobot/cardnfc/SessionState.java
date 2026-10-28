package com.persiarobot.cardnfc;

/** In-memory only, so a process restart cannot reactivate NFC sharing. */
final class SessionState {
    static volatile boolean active;

    private SessionState() { }
}
