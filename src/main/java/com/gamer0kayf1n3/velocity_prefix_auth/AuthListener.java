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
    private final Set<String> pendingPremiumAuth = ConcurrentHashMap.newKeySet();
    private final Set<String> playersToExpectAfterKick;

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
    public AuthListener(Logger logger, Set<String> playersToExpectAfterKick) {
        this.logger = logger;
        this.playersToExpectAfterKick = playersToExpectAfterKick;
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

        if (username.startsWith(".")) return; // never mess with floodgate players!

        boolean isPremium = premiumInfo != null && premiumInfo.uuid != null;

        logger.info("Checked premium status for player {}: {} {}",
                username,
                isPremium ? "premium" : "not premium",
                premiumInfo != null ? "(source: " + premiumInfo.dataSource + ")" : ""
        );

        if (isPremium) {
            pendingPremiumAuth.add(username.toLowerCase());
            event.setResult(PreLoginComponentResult.forceOnlineMode());
            logger.info("Player {} is premium, forcing online mode authentication", username);

            try {
                Object inbound = event.getConnection();
                Object initial = DELEGATE_FIELD.invoke(inbound);
                java.lang.reflect.Method getConnectionMethod = initial.getClass().getMethod("getConnection");
                Object mcConn = getConnectionMethod.invoke(initial);
                java.lang.reflect.Method getChannelMethod = mcConn.getClass().getMethod("getChannel");
                Channel channel = (Channel) getChannelMethod.invoke(mcConn);
                if (channel != null && channel.isActive()) {
                    channel.closeFuture().addListener(f -> {
                        if (pendingPremiumAuth.remove(username.toLowerCase())) {
                            logger.info("Channel closed for {} - adding to expectAfterKick", username);
                            playersToExpectAfterKick.add(username.toLowerCase());
                        } else {
                            logger.info("Channel closed for {}, but they were not in pendingPremiumAuth", username);
                        }
                    });
                }
            } catch (Throwable e) {
                logger.error("Failed to attach channel close listener for {}", username, e);
            }

        } else {
            playersToExpectAfterKick.add(username.toLowerCase());

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

        String pPAcheckString = originalName.toLowerCase();
        if (pendingPremiumAuth.contains(pPAcheckString)) pendingPremiumAuth.remove(pPAcheckString);


        // If the connection is online-mode then they are a premium player and we should not rewrite their username or UUID.
        if (event.isOnlineMode()) {
            logger.info("GameProfileRequestEvent for player {} in online mode, no username rewriting needed", originalName);
            return;
        }

        // At this point either the player is offline/cracked (should be prefixed) or it's an
        // online-mode connection for a known cracked player (rare). Rewrite to add cracked prefix
        // to prevent name collisions with premium players.
        // logger.info("Rewriting GameProfile for player {} to add cracked prefix and prevent name collision with premium player", originalName);

        // String newUsername = String.format("%.16s", c_ + originalName);
        // UUID newUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + newUsername).getBytes());
        // GameProfile rewrittenProfile = new GameProfile(newUuid, newUsername, originalProfile.getProperties());
        // event.setGameProfile(rewrittenProfile);
    }

    static class InboundPacketRewriter extends ChannelDuplexHandler {
        private final Set<String> playersToExpectAfterKick;
        private final Logger logger;

        InboundPacketRewriter(Set<String> playersToExpectAfterKick, Logger logger) {
            this.playersToExpectAfterKick = playersToExpectAfterKick;
            this.logger = logger;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            boolean REVEAL_HEX_ON_PARSE_FAILURE = false; // set to true to log hex of packets that fail parsing as login start packets (could be noisy and reveal sensitive info, so default to false)
            // dO NOT SET THIS TO TRUE UNLESS YOU WANT YOUR CONSOLE CLOGGED AND AUTH DATA LEAKED!
            // This is only really useful for debugging parsing issues with the login start packet, and should be used with caution.
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                buf.markReaderIndex();
                try {
                    String username = parseLoginStartUsername(buf);
                    logger.info("InboundPacketRewriter: parsed username={}, expectAfterKick={}", username, playersToExpectAfterKick);
                    if (username == null && REVEAL_HEX_ON_PARSE_FAILURE) {
                        buf.resetReaderIndex();
                        int bytesToShow = Math.min(32, buf.readableBytes());
                        byte[] bytes = new byte[bytesToShow];
                        buf.getBytes(buf.readerIndex(), bytes);
                        StringBuilder hex = new StringBuilder();
                        for (byte b : bytes) {
                            hex.append(String.format("%02X ", b));
                        }
                        logger.info("null parse - hex: {}", hex.toString().trim());
                        // could spam your console! might reveal ping packets, forwarding secrets, etc.
                    }
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
            try {
                int packetId = readVarInt(buf);
                if (packetId != 0x00) {
                    buf.readerIndex(initialIndex);
                    return null;
                }
                int usernameLength = readVarInt(buf);
                if (usernameLength < 1 || usernameLength > 16) {
                    buf.readerIndex(initialIndex);
                    return null;
                }
                if (buf.readableBytes() < usernameLength) {
                    buf.readerIndex(initialIndex);
                    return null;
                }
                String username = buf.readCharSequence(usernameLength, StandardCharsets.UTF_8).toString();
                buf.readerIndex(initialIndex);
                return username;
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                buf.readerIndex(initialIndex);
                return null;
            }
        }   

        private static ByteBuf rewriteLoginStartPacket(ChannelHandlerContext ctx, ByteBuf original, String newUsername) {
            original.resetReaderIndex();
            readVarInt(original); // skip packet ID
            int usernameLength = readVarInt(original);
            original.skipBytes(usernameLength); // skip original username

            ByteBuf packet = ctx.alloc().buffer();
            writeVarInt(packet, 0x00); // packet ID
            byte[] usernameBytes = newUsername.getBytes(StandardCharsets.UTF_8);
            writeVarInt(packet, usernameBytes.length);
            packet.writeBytes(usernameBytes);
            if (original.isReadable()) {
                packet.writeBytes(original); // UUID and any remaining bytes
            }
            return packet;
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
