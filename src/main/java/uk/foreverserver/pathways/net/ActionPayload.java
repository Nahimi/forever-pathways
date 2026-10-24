package uk.foreverserver.pathways.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.foreverserver.pathways.ForeverPathways;
import uk.foreverserver.pathways.PathwayService;
import uk.foreverserver.pathways.TaskBook;

public record ActionPayload(String action, String stageId) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ActionPayload::action,
            ByteBufCodecs.STRING_UTF8, ActionPayload::stageId,
            ActionPayload::new
    );

    public static ActionPayload sync() {
        return new ActionPayload("sync", "");
    }

    public static ActionPayload commit(String stageId) {
        return new ActionPayload("commit", stageId);
    }

    public static ActionPayload debug(String stageId) {
        return new ActionPayload("debug", stageId);
    }

    public static void handle(ActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            switch (payload.action) {
                case "commit" -> PathwayService.commit(player, payload.stageId);
                case "debug" -> PathwayService.debugToggle(player, payload.stageId);
                case "sync" -> TaskBook.update(player);
                default -> {
                }
            }
            context.reply(PathwayService.snapshot(player));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
