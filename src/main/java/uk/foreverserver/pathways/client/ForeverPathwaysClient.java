package uk.foreverserver.pathways.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import uk.foreverserver.pathways.BlockGuard;
import uk.foreverserver.pathways.ForeverPathways;
import uk.foreverserver.pathways.PathwayClientHooks;
import uk.foreverserver.pathways.net.ActionPayload;

@Mod(value = ForeverPathways.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ForeverPathways.MOD_ID, value = Dist.CLIENT)
public class ForeverPathwaysClient {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.foreverpathways.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.foreverpathways"
    );

    private static int syncTicker;

    /** Belt and braces: if the client is somehow mining a locked block, stop at once rather than predict a break. */
    private static void stopLockedMining(Minecraft minecraft) {
        if (minecraft.gameMode == null || minecraft.player == null || minecraft.level == null
                || !minecraft.gameMode.isDestroying()) {
            return;
        }
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            Item block = minecraft.level.getBlockState(hit.getBlockPos()).getBlock().asItem();
            if (BlockGuard.locked(minecraft.player, block)) {
                minecraft.gameMode.stopDestroyBlock();
            }
        }
    }

    public ForeverPathwaysClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Notices.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(Hud::register);
        PathwayClientHooks.accept = payload -> {
            ClientState.apply(payload);
            PathwaysScreen.apply(payload);
        };
        PathwayClientHooks.notify = Hud::accept;
        BlockGuard.clientLocked = ClientGuard::locked;
        BlockGuard.clientTell = ClientGuard::tell;
        BlockGuard.clientOwns = ClientState::owns;
        NeoForge.EVENT_BUS.addListener(ForeverPathwaysClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(Tooltips::onTooltip);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            ClientState.reset();
            uk.foreverserver.pathways.Difficulty.received(null);
            Hud.reset();
        });
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        stopLockedMining(minecraft);
        while (OPEN.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new PathwaysScreen());
            }
        }
        if (!(minecraft.screen instanceof PathwaysScreen pathways)) {
            syncTicker = 0;
            return;
        }
        if (pathways.isHolding()) {
            return;
        }
        syncTicker++;
        if (syncTicker >= 20) {
            syncTicker = 0;
            PacketDistributor.sendToServer(ActionPayload.sync());
        }
    }
}
