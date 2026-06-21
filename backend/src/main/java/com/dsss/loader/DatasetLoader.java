package com.dsss.loader;

import com.dsss.config.AppProperties;
import com.dsss.store.SearchCountStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Loads the search-count seed data on startup.
 *
 * Default source = <b>ORCAS</b> (Microsoft's Open Resource for Click Analysis in
 * Search): real Bing search queries. Each TSV row is
 *   {@code <queryId> \t <query> \t <docId> \t <url>}
 * and every row is one observed click, so aggregating rows per query yields a
 * real popularity count used as the initial search count.
 *
 * {@code app.dataset.format=pageviews} switches to the Wikipedia pageviews
 * format ({@code <project> <title> <viewcount> <bytes>}) instead.
 *
 * Resolution order: the external file at {@code app.dataset.file} (optionally
 * {@code .gz}) if present, otherwise the small bundled classpath sample so the
 * app always boots. {@code app.dataset.max-rows} caps how many rows are read
 * from a full dump to keep memory bounded. Run scripts/fetch-dataset.sh to pull
 * the full ORCAS dump.
 */
@Component
public class DatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoader.class);

    private final SearchCountStore store;
    private final AppProperties props;

    public DatasetLoader(SearchCountStore store, AppProperties props) {
        this.store = store;
        this.props = props;
    }

    /** Parse the dataset (external file or bundled sample) into the in-memory store. */
    public void loadIntoStore() throws Exception {
        AppProperties.Dataset cfg = props.getDataset();
        Path external = Path.of(cfg.getFile());
        Path externalGz = Path.of(cfg.getFile() + ".gz");

        long start = System.currentTimeMillis();
        long loaded;
        if (Files.exists(external)) {
            log.info("loading dataset from external file: {}", external.toAbsolutePath());
            try (InputStream in = Files.newInputStream(external)) {
                loaded = parse(in, false);
            }
        } else if (Files.exists(externalGz)) {
            log.info("loading dataset from gzipped file: {}", externalGz.toAbsolutePath());
            try (InputStream in = Files.newInputStream(externalGz)) {
                loaded = parse(in, true);
            }
        } else {
            log.warn("external dataset not found at {}; falling back to bundled sample ({})",
                    external.toAbsolutePath(), cfg.getSampleClasspath());
            try (InputStream in = new ClassPathResource(cfg.getSampleClasspath()).getInputStream()) {
                loaded = parse(in, false);
            }
        }
        log.info("dataset loaded: {} rows kept, {} distinct queries in store, {} ms",
                loaded, store.size(), System.currentTimeMillis() - start);
    }

    private long parse(InputStream raw, boolean gzip) throws Exception {
        InputStream in = gzip ? new GZIPInputStream(raw) : raw;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return "pageviews".equalsIgnoreCase(props.getDataset().getFormat())
                    ? parsePageviews(r)
                    : parseOrcas(r);
        }
    }

    /**
     * ORCAS: {@code queryId \t query \t docId \t url}. Each row is one click;
     * we add 1 to the query's count per row, so a query clicked across many
     * documents/sessions accrues a higher popularity. Reading stops at
     * {@code max-rows}.
     */
    private long parseOrcas(BufferedReader r) throws Exception {
        long maxRows = props.getDataset().getMaxRows();
        int sampleMod = Math.max(1, props.getDataset().getSampleMod());
        long rows = 0, kept = 0;
        String line;
        while ((line = r.readLine()) != null) {
            if (rows++ >= maxRows) {
                log.info("reached max-rows safety cap ({}); stopping load", maxRows);
                break;
            }
            int t1 = line.indexOf('\t');
            if (t1 < 0) {
                continue;
            }
            int t2 = line.indexOf('\t', t1 + 1);
            String query = (t2 < 0 ? line.substring(t1 + 1) : line.substring(t1 + 1, t2)).trim();
            if (query.length() < 2 || query.length() > 80) {
                continue;
            }
            // Keep 1 in `sampleMod` queries by a stable hash so the kept set
            // spans the whole file (representative) while memory stays bounded.
            // Every click row of a kept query is still counted, so popularity
            // counts are exact for the kept set.
            if (sampleMod > 1 && Math.floorMod(query.hashCode(), sampleMod) != 0) {
                continue;
            }
            store.load(query, 1);
            kept++;
        }
        return kept;
    }

    /** Wikipedia pageviews: {@code project title viewcount bytes}. */
    private long parsePageviews(BufferedReader r) throws Exception {
        String project = props.getDataset().getProjectFilter();
        long minViews = props.getDataset().getMinViews();
        long maxRows = props.getDataset().getMaxRows();
        long rows = 0, kept = 0;
        String line;
        while ((line = r.readLine()) != null) {
            if (rows++ >= maxRows) {
                break;
            }
            String[] parts = line.split(" ");
            if (parts.length < 3) {
                continue;
            }
            if (project != null && !project.isEmpty() && !project.equals(parts[0])) {
                continue;
            }
            String title = parts[1];
            if (isMetaPage(title)) {
                continue;
            }
            long views;
            try {
                views = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (views < minViews) {
                continue;
            }
            String query = cleanTitle(title);
            if (query.length() < 2) {
                continue;
            }
            store.load(query, views);
            kept++;
        }
        return kept;
    }

    private static boolean isMetaPage(String title) {
        // Wikipedia namespaces / housekeeping pages we don't want as suggestions.
        return title.startsWith("Special:") || title.startsWith("File:")
                || title.startsWith("Wikipedia:") || title.startsWith("Category:")
                || title.startsWith("Template:") || title.startsWith("Help:")
                || title.startsWith("Talk:") || title.startsWith("Portal:")
                || title.equals("Main_Page") || title.startsWith("User:")
                || title.contains(":") && title.indexOf(':') < 12;
    }

    private static String cleanTitle(String title) {
        String t = title.replace('_', ' ');
        try {
            t = URLDecoder.decode(t, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // malformed % escapes -> keep the underscore-stripped form
        }
        return t.trim();
    }
}
