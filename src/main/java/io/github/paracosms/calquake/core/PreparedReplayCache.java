package io.github.paracosms.calquake.core;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Application-session-only immutable prepared replay cache. */
public final class PreparedReplayCache {
    private final ConcurrentMap<String, PreparedReplay> entries = new ConcurrentHashMap<>();

    public Optional<PreparedReplay> get(String signature) {
        return Optional.ofNullable(entries.get(signature));
    }

    public PreparedReplay putIfAbsent(PreparedReplay replay) {
        return entries.computeIfAbsent(replay.inputSignature(), ignored -> replay);
    }

    public int size() { return entries.size(); }
    public void clear() { entries.clear(); }
}
