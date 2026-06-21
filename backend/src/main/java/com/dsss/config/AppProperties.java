package com.dsss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Strongly-typed view over the {@code app.*} block in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Suggest suggest = new Suggest();
    private final Cache cache = new Cache();
    private final Batch batch = new Batch();
    private final Trending trending = new Trending();
    private final Dataset dataset = new Dataset();
    private final Db db = new Db();

    public Suggest getSuggest() { return suggest; }
    public Cache getCache() { return cache; }
    public Batch getBatch() { return batch; }
    public Trending getTrending() { return trending; }
    public Dataset getDataset() { return dataset; }
    public Db getDb() { return db; }

    public static class Suggest {
        private int topK = 10;
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }

    public static class Cache {
        private String server = "localhost:6379";  // the single Redis server (logical mode)
        private int logicalNodes = 3;               // logical nodes = Redis DBs (logical mode)
        // Physical mode: if non-empty, each entry is a separate Redis server
        // (host:port) used as one cache node. Overrides server/logicalNodes.
        private List<String> nodes = new ArrayList<>();
        private boolean embedded = true;
        private int virtualNodes = 150;
        private long ttlMs = 30000;
        private int profileKeyLength = 2;
        public String getServer() { return server; }
        public void setServer(String server) { this.server = server; }
        public int getLogicalNodes() { return logicalNodes; }
        public void setLogicalNodes(int n) { this.logicalNodes = n; }
        public List<String> getNodes() { return nodes; }
        public void setNodes(List<String> nodes) { this.nodes = nodes; }
        public boolean isEmbedded() { return embedded; }
        public void setEmbedded(boolean embedded) { this.embedded = embedded; }
        public int getVirtualNodes() { return virtualNodes; }
        public void setVirtualNodes(int virtualNodes) { this.virtualNodes = virtualNodes; }
        public long getTtlMs() { return ttlMs; }
        public void setTtlMs(long ttlMs) { this.ttlMs = ttlMs; }
        public int getProfileKeyLength() { return profileKeyLength; }
        public void setProfileKeyLength(int v) { this.profileKeyLength = v; }
    }

    public static class Batch {
        private int size = 500;
        private long flushIntervalMs = 2000;
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public long getFlushIntervalMs() { return flushIntervalMs; }
        public void setFlushIntervalMs(long v) { this.flushIntervalMs = v; }
    }

    public static class Trending {
        private double halfLifeSec = 300;
        private int windowSize = 50;
        public double getHalfLifeSec() { return halfLifeSec; }
        public void setHalfLifeSec(double v) { this.halfLifeSec = v; }
        public int getWindowSize() { return windowSize; }
        public void setWindowSize(int v) { this.windowSize = v; }
    }

    public static class Dataset {
        private String format = "orcas";              // orcas | pageviews
        private String file = "./data/orcas.tsv";     // external dump (also tries .gz)
        private String sampleClasspath = "data/orcas-sample.tsv";
        private long maxRows = 25_000_000;            // safety cap on rows read (full ORCAS ~18M)
        private int sampleMod = 8;                    // keep 1 in N queries by hash (bounds memory,
                                                      // spans the whole file); 1 = keep all
        private long minViews = 1;                    // pageviews: min view count
        private String projectFilter = "en";          // pageviews: keep this project
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public String getSampleClasspath() { return sampleClasspath; }
        public void setSampleClasspath(String v) { this.sampleClasspath = v; }
        public long getMaxRows() { return maxRows; }
        public void setMaxRows(long v) { this.maxRows = v; }
        public int getSampleMod() { return sampleMod; }
        public void setSampleMod(int v) { this.sampleMod = v; }
        public long getMinViews() { return minViews; }
        public void setMinViews(long v) { this.minViews = v; }
        public String getProjectFilter() { return projectFilter; }
        public void setProjectFilter(String v) { this.projectFilter = v; }
    }

    public static class Db {
        private boolean embedded = true;              // start embedded Postgres in-process
        private int port = 5433;                      // embedded Postgres port
        private String dataDir = "./data/pgdata";     // persists across restarts
        // Used when embedded=false (external Postgres):
        private String url = "jdbc:postgresql://localhost:5432/postgres";
        private String username = "postgres";
        private String password = "postgres";
        private int seedBatchSize = 5000;             // rows per insert batch when seeding
        public boolean isEmbedded() { return embedded; }
        public void setEmbedded(boolean v) { this.embedded = v; }
        public int getPort() { return port; }
        public void setPort(int v) { this.port = v; }
        public String getDataDir() { return dataDir; }
        public void setDataDir(String v) { this.dataDir = v; }
        public String getUrl() { return url; }
        public void setUrl(String v) { this.url = v; }
        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
        public int getSeedBatchSize() { return seedBatchSize; }
        public void setSeedBatchSize(int v) { this.seedBatchSize = v; }
    }
}
