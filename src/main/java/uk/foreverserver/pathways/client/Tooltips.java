package uk.foreverserver.pathways.client;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import uk.foreverserver.pathways.Tree;

/**
 * A line on every item's tooltip saying which node unlocks it, so you never have to open the
 * screen to find out. Items nothing in the tree claims say nothing at all.
 */
final class Tooltips {
    private Tooltips() {}

    static void onTooltip(ItemTooltipEvent event) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (id == null) {
            return;
        }
        String owner = ClientIndex.get().owner.get(id.toString());
        if (owner == null) {
            return;
        }
        Tree tree = Tree.get();
        Tree.Node node = tree.find(owner);
        if (node == null) {
            return;
        }
        Boolean owns = ClientState.owns(node.id);
        String where = node.title + "  (" + tierName(tree, node.ring) + ")";
        if (Boolean.TRUE.equals(owns)) {
            event.getToolTip().add(Component.literal("Pathways: " + where)
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // Decoration says what it really costs you: the recipe, not the block.
        boolean craftOnly = node.craftOnly || uk.foreverserver.pathways.Locks.everyday(id.toString());
        String label = craftOnly ? "Cannot craft yet: " : "Locked: ";
        event.getToolTip().add(Component.literal(label + where)
                .withStyle(Boolean.FALSE.equals(owns) ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (craftOnly) {
            event.getToolTip().add(Component.literal("Free to place, use and break")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Tree.Node seal = tree.sealFor(node.ring);
        if (seal != null && !Boolean.TRUE.equals(ClientState.owns(seal.id))) {
            event.getToolTip().add(Component.literal("Behind " + seal.title).withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static String tierName(Tree tree, int ring) {
        if (tree.tiers != null && ring >= 0 && ring < tree.tiers.size()) {
            return tree.tiers.get(ring);
        }
        return "Ring " + ring;
    }
}
