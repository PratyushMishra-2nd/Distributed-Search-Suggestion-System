package com.dsss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    public Suggest getSuggest() { return suggest; }
    public Cache getCache() { return cache; }
    public Batch getBatch() { return batch; }
    public Trending getTrending() { return trending; }
    public Dataset getDataset() { return dataset; }

    public static class Suggest {
        private int topK = 10;
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }

    public static class Cache {
        private String server = "localhost:6379";  // the single Redis server
        private int logicalNodes = 3;               // logical nodes = Redis DBs
        private boolean embedded = true;
        private int virtualNodes = 150;
        private long ttlMs = 30000;
        private int profileKeyLength = 2;
        public String getServer() { return server; }
        public void setServer(String server) { this.server = server; }
        public int getLogicalNodes() { return logicalNodes; }
        public void setLogicalNodes(int n) { this.logicalNodes = n; }
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
        private String file = "./data/pageviews";
        private String sampleClasspath = "data/pageviews-sample.txt";
        private long minViews = 1;
        private String projectFilter = "en";
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public String getSampleClasspath() { return sampleClasspath; }
        public void setSampleClasspath(String v) { this.sampleClasspath = v; }
        public long getMinViews() { return minViews; }
        public void setMinViews(long v) { this.minViews = v; }
        public String getProjectFilter() { return projectFilter; }
        public void setProjectFilter(String v) { this.projectFilter = v; }
    }
}
