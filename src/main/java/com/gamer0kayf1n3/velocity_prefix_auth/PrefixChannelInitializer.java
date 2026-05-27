package com.gamer0kayf1n3.velocity_prefix_auth;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.slf4j.Logger;

import java.util.Set;

public class PrefixChannelInitializer extends ChannelInitializer<Channel> {

    private final ChannelInitializer<Channel> original;
    private final Set<String> playersToExpectAfterKick;
    private final Logger logger;

    @SuppressWarnings("unchecked")
    public PrefixChannelInitializer(ChannelInitializer<?> original, Set<String> playersToExpectAfterKick, Logger logger) {
        this.original = (ChannelInitializer<Channel>) original;
        this.playersToExpectAfterKick = playersToExpectAfterKick;
        this.logger = logger;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        // invoke original via reflection since initChannel is protected
        java.lang.reflect.Method m = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
        m.setAccessible(true);
        m.invoke(original, ch);

        if (ch.pipeline().get("frame-decoder") != null) {
            ch.pipeline().addAfter("frame-decoder", "prefix-username-rewriter",
                    new AuthListener.InboundPacketRewriter(playersToExpectAfterKick, logger));
                    //logger.info("[prefix-debug] Added username rewriter after frame-decoder");
        }  else {
            throw new IllegalStateException("Could not find frame-decoder in pipeline! This plugin may be incompatible with this version of Velocity. Please report this to the developer.");
    }
    }
}