package com.gamer0kayf1n3.velocity_prefix_auth;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import io.netty.channel.ChannelInitializer;


@Plugin(
        id = "velocity-prefix-auth",
        name = "VelocityPrefixAuth",
        version = "1.0.0",
        description = "Pre-auth identity normalizer for cracked/premium name collision prevention",
        authors = {"gamer0kayf1n3"},
        dependencies = {}
)


public class VelocityPrefixAuth {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;


    @Inject
    public VelocityPrefixAuth(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        logger.info("VelocityPrefixAuth has been enabled!");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Set<String> playersToExpectAfterKick = ConcurrentHashMap.newKeySet();
        
        server.getEventManager().register(this, new AuthListener(logger, playersToExpectAfterKick));

        try {
            java.lang.reflect.Field cmField = server.getClass().getDeclaredField("cm");
            cmField.setAccessible(true);
            Object cm = cmField.get(server);

            java.lang.reflect.Field initializerHolderField = cm.getClass().getDeclaredField("serverChannelInitializer");
            initializerHolderField.setAccessible(true);
            Object initializerHolder = initializerHolderField.get(cm);

            java.lang.reflect.Method getMethod = initializerHolder.getClass().getMethod("get");
            ChannelInitializer<?> original = (ChannelInitializer<?>) getMethod.invoke(initializerHolder);

            java.lang.reflect.Method setMethod = initializerHolder.getClass().getMethod("set", ChannelInitializer.class);
            setMethod.invoke(initializerHolder, new PrefixChannelInitializer(original, playersToExpectAfterKick, logger));

            logger.info("Successfully injected channel initializer");
        } catch (Throwable e) {
            logger.error("Failed to inject channel initializer", e);
        }
    }

    
}


