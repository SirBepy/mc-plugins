package dev.sirbepy.mc.treemod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * One {@code classes:tree} message, either direction: a UTF-8 JSON object filling the whole payload
 * with no length prefix, because the Paper side (TreeChannel in the Classes plugin) sends and reads
 * raw plugin-message bytes.
 */
public record TreePayload(String json) implements CustomPacketPayload {

    public static final Type<TreePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("classes", "tree"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TreePayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.json().getBytes(StandardCharsets.UTF_8)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new TreePayload(new String(bytes, StandardCharsets.UTF_8));
            });

    @Override
    public Type<TreePayload> type() {
        return TYPE;
    }
}
