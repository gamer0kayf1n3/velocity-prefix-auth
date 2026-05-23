package com.gamer0kayf1n3.velocity_prefix_auth;

import java.nio.file.Path;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;


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
        server.getEventManager().register(this, new AuthListener(logger));
    }

    
}


