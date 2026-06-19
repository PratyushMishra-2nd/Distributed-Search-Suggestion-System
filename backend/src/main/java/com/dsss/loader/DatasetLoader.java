package com.dsss.loader;

import com.dsss.config.AppProperties;
import com.dsss.store.SearchCountStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
 * Source = Wikipedia pageviews dump, a public dataset where each row is
 *   {@code <project> <title> <viewcount> <bytes>}
 * and the view count is used directly as the initial search count. We keep only
 * the configured project (default English Wikipedia) and skip Wikipedia
 * meta-pages. Titles are URL-decoded and underscores become spaces so they read
 * like real queries ("Java (programming language)").
 *
 * Resolution order: the external file at {@code app.dataset.file} (optionally
 * gzipped) if present, otherwise the small bundled classpath sample so the app
 * always boots. Run scripts/fetch-dataset.sh to pull a full >100k-row dump.
 */
@Component
public class DatasetLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoader.class);

    private final SearchCountStore store;
    private final AppProperties props;

    public DatasetLoader(SearchCountStore store, AppProperties props) {
        this.store = store;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
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
        String project = props.getDataset().getProjectFilter();
        long minViews = props.getDataset().getMinViews();
        long kept = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
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
