package uk.foreverserver.pathways.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.foreverserver.pathways.ForeverPathways;

/** The difficulty preset in force on the server, so the client shows the same costs the server charges. */
public record DifficultyPayload(String preset) implements CustomPacketPayload {
    public static final Type<DifficultyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "difficulty"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DifficultyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.preset == null ? "" : payload.preset, 16),
            buf -> new DifficultyPayload(buf.readUtf(16)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
