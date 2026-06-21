package com.dsss.persistence;

import com.dsss.config.AppProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Wires the durable primary store (PostgreSQL).
 *
 * With {@code app.db.embedded=true} (default) a real Postgres binary is started
 * in-process by {@link EmbeddedPostgres} — no Docker — persisting to
 * {@code app.db.data-dir} so data survives restarts. With {@code embedded=false}
 * a Hikari pool connects to an external Postgres at {@code app.db.url}.
 *
 * The {@link DataSource} bean drives Spring's auto-configured {@code JdbcTemplate}.
 */
@Configuration
public class PersistenceConfig {

    private static final Logger log = LoggerFactory.getLogger(PersistenceConfig.class);

    /** Started only in embedded mode; closed on shutdown. */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.db", name = "embedded", havingValue = "true", matchIfMissing = true)
    public EmbeddedPostgres embeddedPostgres(AppProperties props) throws Exception {
        AppProperties.Db cfg = props.getDb();
        Path dataDir = Path.of(cfg.getDataDir()).toAbsolutePath();
        log.info("starting embedded Postgres on port {} (data dir {})", cfg.getPort(), dataDir);
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setPort(cfg.getPort())
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false) // keep data between runs
                .start();
        log.info("embedded Postgres ready on port {}", pg.getPort());
        return pg;
    }

    @Bean
    public DataSource dataSource(AppProperties props, ObjectProvider<EmbeddedPostgres> embeddedPostgres) {
        if (props.getDb().isEmbedded()) {
            // Database "postgres", user "postgres" — the embedded defaults.
            return embeddedPostgres.getObject().getPostgresDatabase();
        }
        AppProperties.Db cfg = props.getDb();
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.getUrl());
        hc.setUsername(cfg.getUsername());
        hc.setPassword(cfg.getPassword());
        hc.setMaximumPoolSize(8);
        return new HikariDataSource(hc);
    }
}
