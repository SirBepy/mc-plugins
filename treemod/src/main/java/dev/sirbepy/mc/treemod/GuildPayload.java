package dev.sirbepy.mc.treemod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * One {@code guild:ui} message, either direction: a UTF-8 JSON object filling the whole payload with
 * no length prefix, the same shape as {@link TreePayload}, because the Paper side (GuildChannel in the
 * Guild plugin) sends and reads raw plugin-message bytes.
 */
public record GuildPayload(String json) implements CustomPacketPayload {

    public static final Type<GuildPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("guild", "ui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuildPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.json().getBytes(StandardCharsets.UTF_8)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new GuildPayload(new String(bytes, StandardCharsets.UTF_8));
            });

    @Override
    public Type<GuildPayload> type() {
        return TYPE;
    }
}
