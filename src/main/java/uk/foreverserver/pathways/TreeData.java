package uk.foreverserver.pathways;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import uk.foreverserver.pathways.net.TreePayload;

/**
 * tree.json as datapack data. The server reads data/foreverpathways/tree.json through its datapacks,
 * so a datapack or KubeJS can replace it, and /reload picks up a change. The mod's own copy is just the
 * lowest-priority datapack. The server then sends the tree it uses to every player, so their screen
 * shows the ring the server enforces.
 */
public final class TreeData {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "tree.json");
    /** The text of the tree the server is using, sent to players as they join or after /reload. */
    private static volatile String json;

    private TreeData() {}

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Loaded>() {
            @Override
            protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
                Optional<Resource> resource = manager.getResource(ID);
                if (resource.isEmpty()) {
                    return null;
                }
                try (InputStream in = resource.get().open()) {
                    return new Loaded(new String(in.readAllBytes(), StandardCharsets.UTF_8), resource.get().sourcePackId());
                } catch (IOException exception) {
                    ForeverPathways.LOGGER.error("Pathways could not read tree.json from the datapacks", exception);
                    return null;
                }
            }

            @Override
            protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
                if (loaded == null) {
                    return;
                }
                Tree tree;
                try {
                    tree = Tree.parse(loaded.text);
                } catch (RuntimeException exception) {
                    ForeverPathways.LOGGER.error("Pathways: tree.json from '{}' is broken, so the tree in use is kept: {}",
                            loaded.pack, exception.getMessage());
                    return;
                }
                json = loaded.text;
                boolean ours = loaded.pack == null || loaded.pack.equals("mod_resources")
                        || loaded.pack.equals("mod/" + ForeverPathways.MOD_ID);
                Tree.replace(tree, ours ? "built-in" : "datapack '" + loaded.pack + "'");
                ForeverPathways.LOGGER.info("Pathways loaded tree.json from {} ({} nodes before fitting)",
                        Tree.source(), tree.nodes.size());
                StageWriter.write(false);
            }
        });
    }

    /** Sends the tree to players joining, and to everyone after /reload. */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        String text = json;
        if (text == null) {
            return;
        }
        TreePayload payload = TreePayload.of(text);
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    /** On a client: use the tree the server sent. In single player the server's tree is already in use. */
    public static void received(TreePayload payload) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return;
        }
        try {
            Tree.replace(Tree.parse(payload.json()), "the server");
        } catch (RuntimeException exception) {
            ForeverPathways.LOGGER.error("Pathways could not read the tree the server sent; using the built-in one", exception);
        }
    }

    /** On a client leaving a server: back to the built-in tree. */
    public static void leftServer() {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            Tree.useBuiltIn();
        }
    }

    private record Loaded(String text, String pack) {}
}
