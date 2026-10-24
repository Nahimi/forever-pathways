package uk.foreverserver.pathways.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.foreverserver.pathways.ForeverPathways;

/**
 * One moment worth showing on the HUD: a task or bounty moved forward, or finished. The server sends it to the
 * player it concerns; whether anything is drawn, and how, is that player's own choice in the client config.
 *
 * @param kind     {@link #TASK_PROGRESS}, {@link #BOUNTY_PROGRESS}, {@link #TASK_DONE} or {@link #BOUNTY_DONE}
 * @param key      what this is about (node id or bounty id), so a running popup is updated rather than stacked
 * @param title    the task or bounty title, as written in the tree
 * @param subtitle the node a task belongs to, or the bounty tier
 * @param item     icon item id
 * @param progress how far along, after the change
 * @param goal     how far it has to go
 * @param reward   Focal Shards paid (on completion) or on offer (on progress)
 */
public record NotifyPayload(int kind, String key, String title, String subtitle, String item, long progress,
                            long goal, int reward) implements CustomPacketPayload {
    public static final int TASK_PROGRESS = 0;
    public static final int BOUNTY_PROGRESS = 1;
    public static final int TASK_DONE = 2;
    public static final int BOUNTY_DONE = 3;

    private static final int MAX = 512;

    public static final Type<NotifyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "notify"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NotifyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.kind);
                buf.writeUtf(nz(payload.key), MAX);
                buf.writeUtf(nz(payload.title), MAX);
                buf.writeUtf(nz(payload.subtitle), MAX);
                buf.writeUtf(nz(payload.item), MAX);
                buf.writeVarLong(payload.progress);
                buf.writeVarLong(payload.goal);
                buf.writeVarInt(payload.reward);
            },
            buf -> new NotifyPayload(buf.readVarInt(), buf.readUtf(MAX), buf.readUtf(MAX), buf.readUtf(MAX),
                    buf.readUtf(MAX), buf.readVarLong(), buf.readVarLong(), buf.readVarInt())
    );

    private static String nz(String text) {
        return text == null ? "" : text;
    }

    public boolean done() {
        return kind == TASK_DONE || kind == BOUNTY_DONE;
    }

    public boolean bounty() {
        return kind == BOUNTY_PROGRESS || kind == BOUNTY_DONE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
