package uk.foreverserver.pathways.mixin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.enviouse.progressivestages.server.ServerEventHandler;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * ProgressiveStages sends a player its stage definitions, compiled snapshot and lock table every time they join a
 * level, which includes every respawn and change of dimension. Those only change on login, so they are sent on the
 * first join after it and skipped on the rest.
 */
@Mixin(value = ServerEventHandler.class, remap = false)
public class RespawnSyncMixin {
    private static final Set<UUID> FULLY_SYNCED = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> SKIP_HEAVY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "onPlayerJoin", at = @At("HEAD"), remap = false)
    private static void foreverpathways$decideJoinSync(EntityJoinLevelEvent event, CallbackInfo info) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SKIP_HEAVY.set(FULLY_SYNCED.contains(player.getUUID()));
            FULLY_SYNCED.add(player.getUUID());
        } else {
            SKIP_HEAVY.set(Boolean.FALSE);
        }
    }

    @Inject(method = "onPlayerLogout", at = @At("HEAD"), remap = false)
    private static void foreverpathways$forgetJoinSync(PlayerEvent.PlayerLoggedOutEvent event, CallbackInfo info) {
        FULLY_SYNCED.remove(event.getEntity().getUUID());
    }

    @Redirect(method = "onPlayerJoin", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/enviouse/progressivestages/common/network/NetworkHandler;sendStageDefinitionsSync(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void foreverpathways$definitions(ServerPlayer player) {
        if (!Boolean.TRUE.equals(SKIP_HEAVY.get())) {
            NetworkHandler.sendStageDefinitionsSync(player);
        }
    }

    @Redirect(method = "onPlayerJoin", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/enviouse/progressivestages/common/network/NetworkHandler;sendCompiledSnapshot(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void foreverpathways$snapshot(ServerPlayer player) {
        if (!Boolean.TRUE.equals(SKIP_HEAVY.get())) {
            NetworkHandler.sendCompiledSnapshot(player);
        }
    }

    @Redirect(method = "onPlayerJoin", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/enviouse/progressivestages/common/network/NetworkHandler;sendLockSync(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void foreverpathways$locks(ServerPlayer player) {
        if (!Boolean.TRUE.equals(SKIP_HEAVY.get())) {
            NetworkHandler.sendLockSync(player);
        }
    }
}
