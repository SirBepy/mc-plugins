package dev.sirbepy.mc.treemod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * One {@code haru:quest} message, either direction: a UTF-8 JSON object filling the whole payload with
 * no length prefix, same shape as {@link TreePayload} for {@code classes:tree} (QuestChannel in the
 * Haru plugin sends and reads raw plugin-message bytes on the Paper side).
 */
public record QuestPayload(String json) implements CustomPacketPayload {

    public static final Type<QuestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("haru", "quest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.json().getBytes(StandardCharsets.UTF_8)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new QuestPayload(new String(bytes, StandardCharsets.UTF_8));
            });

    @Override
    public Type<QuestPayload> type() {
        return TYPE;
    }
}
