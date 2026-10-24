package uk.foreverserver.pathways.client;

import com.enviouse.progressivestages.client.ClientLockCache;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import uk.foreverserver.pathways.Tree;

/**
 * The client half of {@link uk.foreverserver.pathways.BlockGuard}. Pathways decides most locks itself
 * from the owned set the server pushes; this adds ProgressiveStages' own client cache, for locks
 * from other packs, and shows the "locked" message.
 */
final class ClientGuard {
    private static long lastTold;
    private static Item lastItem;

    private ClientGuard() {}

    static boolean locked(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return false;
        }
        try {
            return ClientLockCache.isModLocked(id.getNamespace()) || !ClientLockCache.playerOwnsAllStagesFor(id);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static void tell(Item item) {
        long now = System.currentTimeMillis();
        if (item == lastItem && now - lastTold < 1500) {
            return;
        }
        lastItem = item;
        lastTold = now;
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String owner = id == null ? null : ClientIndex.get().owner.get(id.toString());
        Tree.Node node = owner == null ? null : Tree.get().find(owner);
        Component name = new ItemStack(item).getHoverName();
        Component text = node == null
                ? Component.literal("Locked: ").append(name)
                : Component.literal("Locked: ").append(name).append(Component.literal("  |  " + node.title));
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }
}
