package uk.foreverserver.pathways;

import java.util.function.Consumer;

import uk.foreverserver.pathways.net.NotifyPayload;
import uk.foreverserver.pathways.net.StatePayload;

public final class PathwayClientHooks {
    public static Consumer<StatePayload> accept = payload -> {};
    public static Consumer<NotifyPayload> notify = payload -> {};

    private PathwayClientHooks() {}
}
