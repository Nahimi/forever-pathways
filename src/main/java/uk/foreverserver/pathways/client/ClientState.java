package uk.foreverserver.pathways.client;

import java.util.Set;

import uk.foreverserver.pathways.net.StatePayload;

/**
 * What the client knows about its own player: which nodes they own. The server pushes it on
 * join and whenever it changes, and BlockGuard reads it to refuse locked actions before the
 * client predicts them.
 */
final class ClientState {
    private static Set<String> owned = Set.of();
    private static boolean known;

    private ClientState() {}

    static void apply(StatePayload payload) {
        owned = payload.ownedSet();
        known = true;
    }

    static void reset() {
        owned = Set.of();
        known = false;
    }

    /** True or false once the server has told us, null before then. */
    static Boolean owns(String nodeId) {
        return known ? owned.contains(nodeId) : null;
    }
}
