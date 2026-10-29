package uk.foreverserver.pathways;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A boss cannot be fought before its kill would count.
 *
 * The eyes come from six bosses and nowhere else, so meeting one early used to be a waste: you killed
 * it, the eye dropped into a node you did not have, and the next one was a long wait away. Now the
 * boss simply will not take damage from you until the node that asks for it is reachable, which is
 * the same moment its task starts counting.
 *
 * Only players are refused. The boss fights back, other mobs hurt it as usual, and a player who has
 * the node fights it normally even with someone else present.
 */
public final class BossGuard {
    /** Entity id to the node whose task asks for it, worked out once from the tree. */
    private static Map<String, List<Tree.Node>> asked;
    private static final Map<java.util.UUID, Long> TOLD = new HashMap<>();

    private BossGuard() {}

    public static void reset() {
        asked = null;
    }

    private static Map<String, List<Tree.Node>> asked() {
        if (asked == null) {
            Map<String, List<Tree.Node>> map = new HashMap<>();
            Tree tree = Tree.get();
            for (Tree.Node eye : tree.nodes) {
                if (!eye.bossTask || !eye.hasTask() || !eye.task.guard || eye.task.target == null) {
                    continue;
                }
                for (String raw : eye.task.target.split(",")) {
                    String boss = raw.trim();
                    if (boss.isEmpty() || map.containsKey(boss)) {
                        continue;
                    }
                    List<Tree.Node> nodes = new ArrayList<>();
                    for (Tree.Node node : tree.nodes) {
                        if (node.hasTask() && "kill".equals(node.task.kind) && BossEyes.names(node.task.target, boss)) {
                            nodes.add(node);
                        }
                    }
                    map.put(boss, nodes);
                }
            }
            asked = map;
        }
        return asked;
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        if (hurt.level().isClientSide()) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(hurt.getType());
        if (type == null) {
            return;
        }
        List<Tree.Node> nodes = asked().get(type.toString());
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (Tree.Node node : nodes) {
            if (ready(player, node)) {
                return;
            }
        }
        event.setCanceled(true);
        tell(player, nodes.get(0));
    }

    /** True once this player's node is reachable, which is when its kills begin to count. */
    private static boolean ready(ServerPlayer player, Tree.Node node) {
        try {
            if (ProgressiveStagesAPI.hasStage(player, node.id)) {
                return true;
            }
            return ProgressiveStagesAPI.getCounter(player, "fpa_" + node.key()) != 0;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    /** Says why, about once every five seconds, so a long fight is not a wall of text. */
    private static void tell(ServerPlayer player, Tree.Node node) {
        long now = player.tickCount;
        Long last = TOLD.get(player.getUUID());
        if (last != null && now - last < 100) {
            return;
        }
        TOLD.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal("You cannot fight this yet. It belongs to ")
                .append(Component.literal(node.title).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(", and the kill would not count."))
                .withStyle(ChatFormatting.RED));
    }
}
