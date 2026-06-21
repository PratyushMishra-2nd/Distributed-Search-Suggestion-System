package com.dsss.cache;

import com.dsss.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.embedded.RedisServer;

/**
 * Boots a single real {@code redis-server} in-process when
 * {@code app.cache.embedded=true}. The embedded-redis library ships a platform
 * redis-server binary, so it's a genuine Redis instance — no Docker required.
 * The logical cache nodes are separate Redis logical DBs on this one server.
 *
 * Set {@code app.cache.embedded=false} to skip this and point the app at an
 * externally-managed Redis instead.
 *
 * {@link CacheRouter} depends on this bean so the server is up before any Jedis
 * pool tries to connect.
 */
@Component
public class EmbeddedRedisManager {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisManager.class);

    private final AppProperties props;
    private RedisServer server;

    public EmbeddedRedisManager(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void start() {
        if (!props.getCache().isEmbedded()) {
            log.info("embedded Redis disabled (app.cache.embedded=false); expecting external Redis");
            return;
        }
        int port = portOf(props.getCache().getServer());
        try {
            server = RedisServer.newRedisServer()
                    .port(port)
                    .setting("maxmemory 256M")
                    .setting("maxmemory-policy allkeys-lru")
                    .build();
            server.start();
            log.info("embedded Redis server started on port {} ({} logical nodes)",
                    port, props.getCache().getLogicalNodes());
        } catch (Exception e) {
            log.error("failed to start embedded Redis on port {}: {}", port, e.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                log.warn("error stopping embedded Redis: {}", e.getMessage());
            }
        }
    }

    private static int portOf(String hostPort) {
        int idx = hostPort.lastIndexOf(':');
        return Integer.parseInt(hostPort.substring(idx + 1).trim());
    }
}
