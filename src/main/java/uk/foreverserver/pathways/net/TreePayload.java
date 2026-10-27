package uk.foreverserver.pathways.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.foreverserver.pathways.ForeverPathways;

/**
 * The tree.json the server is using, so a client shows the same ring the server enforces even when a
 * datapack or KubeJS has changed it. Sent gzipped: the file is a quarter of a megabyte, a tenth of
 * that packed.
 */
public record TreePayload(byte[] gzipped) implements CustomPacketPayload {
    private static final int MAX = 4 * 1024 * 1024;

    public static final Type<TreePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TreePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByteArray(payload.gzipped),
            buf -> new TreePayload(buf.readByteArray(MAX)));

    public static TreePayload of(String json) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not pack the tree", exception);
        }
        return new TreePayload(out.toByteArray());
    }

    public String json() {
        try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not unpack the tree", exception);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
