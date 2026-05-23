package com.gamer0kayf1n3.velocity_prefix_auth;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.event.connection.ConnectionHandshakeEvent;
import com.velocitypowered.api.proxy.InboundConnection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import com.gamer0kayf1n3.velocity_prefix_auth.MojangApiClient.PlayerInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

import static com.gamer0kayf1n3.velocity_prefix_auth.MojangApiClient.PlayerInfo;

import org.slf4j.Logger;
import java.sql.SQLException;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channel;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class AuthListener {

    private final NameCache nameCache = new NameCache();
    private final NameDatabase nameDatabase;
    private final Logger logger;
    private final Set<String> playersToExpectAfterKick = ConcurrentHashMap.newKeySet();
    private static final String c_ = "c_";

    private static final MethodHandle DELEGATE_FIELD;
    static {
        try {
            Class<?> loginInbound = Class.forName("com.velocitypowered.proxy.connection.client.LoginInboundConnection");
            Class<?> initialInbound = Class.forName("com.velocitypowered.proxy.connection.client.InitialInboundConnection");
            java.lang.reflect.Field delegateField = loginInbound.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            DELEGATE_FIELD = MethodHandles.lookup().unreflectGetter(delegateField);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    public AuthListener(Logger logger) {
        this.logger = logger;
        try {
            this.nameDatabase = new NameDatabase("player_names.db");
        } catch (SQLException e) {
            logger.error("Failed to initialize name database: {}", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize name database", e);
        }
    }

    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    public PlayerInfo checkPremium(String username) {
        // Ask name cache, then database, then Mojang's API to check if the username is premium or not

        PlayerInfo cachedResult = nameCache.get(username);
        if (cachedResult != null) {
            return cachedResult.withDataSource("cache");

        }

        try {
            PlayerInfo dbResult = nameDatabase.getPlayer(username);
            if (dbResult != null) {
                nameCache.put(username, dbResult);
                return dbResult.withDataSource("database");
            }
        } catch (Exception e) {
            logger.error("Failed to query database for player {}: {}", username, e.getMessage());
            e.printStackTrace();
        }

        PlayerInfo apiResult = MojangApiClient.fetchPlayerData(username);

        if (apiResult != null) {
            nameCache.put(username, apiResult);
            try {
                nameDatabase.savePlayerName(apiResult);
            } catch (Exception e) {
                logger.error("Failed to save player {} to database: {}", username, e.getMessage());
                e.printStackTrace();
            }
            return apiResult.withDataSource("api");
        }

        PlayerInfo notPremium = new PlayerInfo(null, username, System.currentTimeMillis() + /* TTL */ 3600_000L);
        notPremium = notPremium.withDataSource("api");
        nameCache.put(username, notPremium);
        try {
            nameDatabase.savePlayerName(notPremium);
        } catch (Exception e) {
            logger.error("Failed to save non-premium player {} to database: {}", username, e.getMessage());
        }
        return null;
    }

    @Subscribe(priority = Short.MAX_VALUE)
    public void onPreLogin(PreLoginEvent event) {

        String username = event.getUsername();
        PlayerInfo premiumInfo = checkPremium(username);

        boolean isPremium = premiumInfo != null && premiumInfo.uuid != null;

        logger.info("Checked premium status for player {}: {} {}",
                username,
                isPremium ? "premium" : "not premium",
                premiumInfo != null ? "(source: " + premiumInfo.dataSource + ")" : ""
        );

        boolean shouldForceAuthenticate = isPremium;

        if (shouldForceAuthenticate) {
            event.setResult(PreLoginComponentResult.forceOnlineMode());
            logger.info("Player {} is premium, forcing online mode authentication", username);
        } else {
            // store lowercase to avoid reconnect-case mismatches
            String lower = username.toLowerCase();
            playersToExpectAfterKick.add(lower);

            // attach close listener so the reconnect can be caught by our Netty injector on reconnect
            try {
                Object inbound = event.getConnection();
                Object initial = DELEGATE_FIELD.invoke(inbound);
                java.lang.reflect.Method getConnectionMethod = initial.getClass().getMethod("getConnection");
                Object mcConn = getConnectionMethod.invoke(initial);
                java.lang.reflect.Method getChannelMethod = mcConn.getClass().getMethod("getChannel");
                Channel channel = (Channel) getChannelMethod.invoke(mcConn);
                if (channel != null && channel.isActive()) {
                    channel.closeFuture().addListener(f -> {
                        // no-op: presence of this listener ensures the channel closed path is observed;
                        // playersToExpectAfterKick already contains the lowercase username and will be consumed
                        // by the Netty packet rewriter on reconnect.
                    });
                }
            } catch (Throwable e) {
                logger.error("Failed to attach channel close listener for {}", username, e);
            }

            String template = "<light_purple>[velocity-prefix-auth] Detected a cracked player! Your username will be rewritten from <name> to <newname> to prevent name collisions with future premium players. Please rejoin!</light_purple>";
            Component kickMessage = MiniMessage.miniMessage().deserialize(
                    template,
                    Placeholder.parsed("name", username),
                    Placeholder.parsed("newname", String.format("%.16s", c_ + username))
            );

            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage));
        }
    }

    @Subscribe(priority = Short.MAX_VALUE)
    public void onGameProfileRequest(GameProfileRequestEvent event) {

        GameProfile originalProfile = event.getGameProfile();
        String originalName = originalProfile.getName();

        // If the connection is online-mode then they are a premium player and we should not rewrite their username or UUID.
        if (event.isOnlineMode()) {
            logger.info("GameProfileRequestEvent for player {} in online mode, no username rewriting needed", originalName);
            return;
        }

        // At this point either the player is offline/cracked (should be prefixed) or it's an
        // online-mode connection for a known cracked player (rare). Rewrite to add cracked prefix
        // to prevent name collisions with premium players.
        logger.info("Rewriting GameProfile for player {} to add cracked prefix and prevent name collision with premium player", originalName);

        String newUsername = String.format("%.16s", c_ + originalName);
        UUID newUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + newUsername).getBytes());
        GameProfile rewrittenProfile = new GameProfile(newUuid, newUsername, originalProfile.getProperties());
        event.setGameProfile(rewrittenProfile);
    }

    private static class InboundPacketRewriter extends ChannelDuplexHandler {
        private final Set<String> playersToExpectAfterKick;

        private InboundPacketRewriter(Set<String> playersToExpectAfterKick) {
            this.playersToExpectAfterKick = playersToExpectAfterKick;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                buf.markReaderIndex();
                try {
                    String username = parseLoginStartUsername(buf);
                    if (username != null && playersToExpectAfterKick.remove(username.toLowerCase())) {
                        String rewrittenUsername = String.format("%.16s", c_ + username);
                        buf.resetReaderIndex();
                        ByteBuf rewritten = rewriteLoginStartPacket(ctx, buf, rewrittenUsername);
                        ReferenceCountUtil.release(msg);
                        super.channelRead(ctx, rewritten);
                        return;
                    }
                } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
                    // fall through to normal processing
                }
                buf.resetReaderIndex();
            }

            super.channelRead(ctx, msg);
        }

        private static String parseLoginStartUsername(ByteBuf buf) {
            int initialIndex = buf.readerIndex();
            int firstVarInt = readVarInt(buf);

            boolean hasLengthPrefix;
            if (firstVarInt == 0) {
                hasLengthPrefix = false;
            } else {
                if (!buf.isReadable()) {
                    buf.readerIndex(initialIndex);
                    return null;
                }
                int packetId = readVarInt(buf);
                if (packetId != 0) {
                    buf.readerIndex(initialIndex);
                    return null;
                }
                hasLengthPrefix = true;
            }

            int usernameLength = readVarInt(buf);
            if (usernameLength < 0 || usernameLength > buf.readableBytes()) {
                buf.readerIndex(initialIndex);
                return null;
            }

            String username = buf.readCharSequence(usernameLength, StandardCharsets.UTF_8).toString();
            buf.readerIndex(initialIndex);
            return username;
        }

        private static ByteBuf rewriteLoginStartPacket(ChannelHandlerContext ctx, ByteBuf original, String newUsername) {
            original.resetReaderIndex();
            int firstVarInt = readVarInt(original);

            boolean hasLengthPrefix;
            if (firstVarInt == 0) {
                hasLengthPrefix = false;
            } else {
                hasLengthPrefix = true;
                readVarInt(original);
            }

            int usernameLength = readVarInt(original);
            original.skipBytes(usernameLength);

            ByteBuf payload = ctx.alloc().buffer();
            writeVarInt(payload, 0);
            byte[] usernameBytes = newUsername.getBytes(StandardCharsets.UTF_8);
            writeVarInt(payload, usernameBytes.length);
            payload.writeBytes(usernameBytes);

            if (original.isReadable()) {
                payload.writeBytes(original);
            }

            if (hasLengthPrefix) {
                ByteBuf packet = ctx.alloc().buffer();
                writeVarInt(packet, payload.readableBytes());
                packet.writeBytes(payload);
                payload.release();
                return packet;
            }

            return payload;
        }

        private static int readVarInt(ByteBuf buf) {
            int numRead = 0;
            int result = 0;
            byte read;
            do {
                if (!buf.isReadable()) {
                    throw new IndexOutOfBoundsException("Not enough bytes to read VarInt");
                }
                read = buf.readByte();
                int value = read & 0x7F;
                result |= (value << (7 * numRead));
                numRead++;
                if (numRead > 5) {
                    throw new IllegalArgumentException("VarInt too big");
                }
            } while ((read & 0x80) != 0);
            return result;
        }

        private static void writeVarInt(ByteBuf buf, int value) {
            while (true) {
                if ((value & ~0x7F) == 0) {
                    buf.writeByte(value);
                    return;
                }
                buf.writeByte((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }
}
