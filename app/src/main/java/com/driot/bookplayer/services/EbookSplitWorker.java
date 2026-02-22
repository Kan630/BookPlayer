package com.driot.bookplayer.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.ebooks.EpubCommonHelper;
import com.driot.bookplayer.ebooks.EpubGutenbergHelper;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.ebooks.DocxLowLevelHelper;
import com.driot.bookplayer.ebooks.OdtLowLevelHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.ebooks.EpubLowLevelHelper;
import com.driot.bookplayer.ebooks.Fb2LowLevelHelper;
import com.driot.bookplayer.utils.log.KanLogger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EbookSplitWorker extends ImportWorker {

    // Keep existing label for compatibility with UI/strings
    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_SPLIT_EBOOK;

    private final Context context;

    public EbookSplitWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        emitTaskStart(TASK_NAME, context.getString(R.string.import_task_ebook_split) + " "
                + context.getString(R.string.import_task_start));
        ImportJob j = jobOrFail();

        final String ebookPath = ImportHelper.getSourceFilePathForWorker(j);
        final String destinationFolderPath = j.futureFolderPath;
        final String sourceLocation = j.sourceLocation;

        myLogD("--------------------------------------------------------------------------");
        myLog("ebookPath = " + ebookPath);
        myLog("destinationFolderPath = " + destinationFolderPath);
        myLog("-------------------------------------");
        final String ebookType = guessTypeFromPath(ebookPath); // keep that line here to get some log if null =>
                                                               // throw...
        myLog("computed ebookType = " + ebookType);
        myLog("source Location = " + sourceLocation);
        myLogD("--------------------------------------------------------------------------");

        // Optionally enter foreground:
        // setForegroundEarly(buildForegroundInfo());

        if (ebookPath == null || destinationFolderPath == null || String.valueOf(ebookPath).isEmpty()
                || String.valueOf(destinationFolderPath).isEmpty()) {
            emitFailed(TASK_NAME, "Missing input data for EbookSplitWorker",
                    getApplicationContext().getString(R.string.invalid_resource));
            myLogEE(null, "Missing input data for EbookSplitWorker");
            return Result.failure();
        }

        FirebaseAnalyticsHelper.tellAnalyticsEbookWorker(ebookType, sourceLocation);

        boolean ok = splitEbook(ebookPath, destinationFolderPath, ebookType, sourceLocation);
        return ok ? Result.success() : Result.failure();
    }

    private boolean splitEbook(String ebookPath, String destinationFolderPath, String ebookType,
            String sourceLocation) {
        Context ctx = getApplicationContext();
        try {
            File outFolder = new File(destinationFolderPath);
            if (!outFolder.exists() && !outFolder.mkdirs()) {
                emitFailed(TASK_NAME, "failed_to_create_destination_folder : " + destinationFolderPath,
                        context.getString(R.string.failed_to_create_destination_folder) + ": " + destinationFolderPath);
                return false;
            }

            // Build a Uri from file path
            Uri uri = (ebookPath.startsWith("content://") || ebookPath.startsWith("file://"))
                    ? Uri.parse(ebookPath)
                    : Uri.fromFile(new File(ebookPath));

            // Extract (cover + chapter files) using the appropriate helper
            Bitmap cover;
            List<File> chapters;

            if ("fb2".equals(ebookType)) {
                emitStepProgress(TASK_NAME, 1, "Parsing FB2…");
                Fb2LowLevelHelper.ExtractResult result = Fb2LowLevelHelper.extractAll(ctx, uri);
                cover = result.coverBitmap;
                chapters = result.chapterFiles;
            } else if ("epub".equals(ebookType)) {
                emitStepProgress(TASK_NAME, 1, "Parsing EPUB…");

                // Determine which helper to use based on setting
                String splitMode = Option.getEpubSplitMode();
                boolean useTocBased = false;

                if ("toc".equals(splitMode)) {
                    useTocBased = true;
                    myLogD("EPUB split mode: TOC-based (forced)");
                } else if ("spine".equals(splitMode)) {
                    useTocBased = false;
                    myLogD("EPUB split mode: Spine-based (forced)");
                } else {
                    // "auto" mode: check if TOC is available and well-structured
                    useTocBased = shouldUseTocBasedSplitting(ctx, uri);
                    myLogD("EPUB split mode: Auto -> " + (useTocBased ? "TOC-based" : "Spine-based"));
                }

                if (useTocBased) {
                    EpubGutenbergHelper.ExtractResult result = EpubGutenbergHelper.extractAll(ctx, uri);
                    cover = result.coverBitmap;
                    chapters = result.chapterFiles;
                } else {
                    EpubLowLevelHelper.ExtractResult result = EpubLowLevelHelper.extractAll(ctx, uri);
                    cover = result.coverBitmap;
                    chapters = result.chapterFiles;
                }
            } else if ("odt".equals(ebookType)) {
                emitStepProgress(TASK_NAME, 1, "Parsing ODT…");
                OdtLowLevelHelper.ExtractResult result = OdtLowLevelHelper.extractAll(ctx, uri);
                cover = result.coverBitmap;
                chapters = result.chapterFiles;
            } else if ("docx".equals(ebookType)) {
                emitStepProgress(TASK_NAME, 1, "Parsing DOCX…");
                DocxLowLevelHelper.ExtractResult result = DocxLowLevelHelper.extractAll(ctx, uri,
                        Option.getDocxSplitIntoChapters());
                cover = result.coverBitmap;
                chapters = result.chapterFiles;
            } else {
                emitFailed(TASK_NAME, "unsupported_ebook_type: [" + ebookType + "]",
                        ctx.getString(R.string.Unsupported_ebook_type) + ". (" + ebookType + ")");
                return false;
            }

            if (chapters == null || chapters.isEmpty()) {
                emitFailed(TASK_NAME, "no_chapters_found : [" + ebookType + "]",
                        ctx.getString(R.string.No_chapters_found));
                return false;
            }

            // Save cover image if present (JPEG to keep previous behavior)
            if (cover != null && !isStopped()) {
                try (FileOutputStream fos = new FileOutputStream(new File(outFolder, "cover.jpg"))) {
                    cover.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.flush();
                } catch (Exception e) {
                    myLogEE(e, "Saving cover.jpg failed");
                    // Non-fatal
                }
            }

            // Prepare output names (keep indices, use chapter filename as title)
            Set<String> usedNames = new HashSet<>();
            DecimalFormat numFmt = new DecimalFormat("000");
            final int total = chapters.size();

            for (int i = 0; i < total; i++) {
                if (isStopped()) {
                    emitCancelled(TASK_NAME);
                    return false;
                }

                File chapterFile = chapters.get(i);

                // Our helpers already wrote *plain text* with preserved newlines.
                String text = readUtf8File(chapterFile);

                // Derive title from file name (remove ###_ prefix and extension) for display,
                // but keep the numeric prefix in the actual output filename so folder sorting
                // preserves reading order (important for Gutenberg & structured EPUBs).
                String title = titleFromFileName(chapterFile.getName());
                if (title == null || title.trim().isEmpty()) {
                    title = "chapter" + numFmt.format(i + 1);
                }
                title = toSafeFilename(title.trim());

                String prefix = leadingIndexFromFileName(chapterFile.getName());
                if (prefix == null) {
                    prefix = numFmt.format(i + 1);
                }

                String outBase = prefix + "_" + title;
                outBase = ensureUnique(usedNames, outBase);
                usedNames.add(outBase.toLowerCase(Locale.ROOT));

                // Write as UTF-8 .txt (preserve newlines; do minimal cleanup only)
                File out = new File(outFolder, outBase + ".txt");
                writeUtf8(out, cleanTextKeepParagraphs(text));

                int progress = (int) Math.round(((i + 1) * 100.0) / total);
                String progressText = "Splitting " + ebookType.toUpperCase(Locale.ROOT) + ": "
                        + (i + 1) + "/" + total + "\n\n" + title;
                emitStepProgress(TASK_NAME, progress, progressText);
                myLogD(progress + "% - " + progressText.replace("\n", " - "));
            }

            // Reuse existing completion hook for EPUB (keeps app logic unchanged)
            emitTaskCompleted(TASK_NAME, outFolder.getAbsolutePath(), ctx.getString(R.string.import_task_ebook_split)
                    + " " + context.getString(R.string.done_));
            return true;

        } catch (Exception e) {
            myLogEE(e, "splitEbook");
            emitFailed(TASK_NAME, e.getMessage(), null);
            return false;
        }
    }

    // ---------- helpers ----------

    /**
     * Checks if TOC-based splitting should be used in auto mode.
     * Returns true if TOC exists and has a reasonable number of entries (10-1000).
     */
    private static boolean shouldUseTocBasedSplitting(Context ctx, Uri epubUri) {
        try {
            // Read EPUB zip
            java.util.Map<String, byte[]> zip = EpubCommonHelper.readZip(epubUri, ctx);
            if (zip == null || zip.isEmpty()) {
                KanLogger.myLogW("Auto mode: Cannot read EPUB zip, using spine-based");
                return false;
            }

            // Find container.xml
            byte[] container = zip.get("META-INF/container.xml");
            if (container == null) {
                KanLogger.myLogW("Auto mode: container.xml not found, using spine-based");
                return false;
            }

            // Find OPF path
            String opfPath = EpubCommonHelper.findOpfPath(container);
            if (opfPath == null) {
                KanLogger.myLogW("Auto mode: OPF not found, using spine-based");
                return false;
            }

            // Read OPF
            byte[] opfBytes = zip.get(opfPath);
            if (opfBytes == null) {
                KanLogger.myLogW("Auto mode: OPF bytes missing, using spine-based");
                return false;
            }

            // Parse OPF to find nav href
            EpubGutenbergHelper.OpfInfo opf = parseOpfForNav(opfBytes);
            String navHref = findNavHref(opf);
            if (navHref == null) {
                KanLogger.myLogD("Auto mode: No nav item found, using spine-based");
                return false;
            }

            // Read nav file
            String basePath = EpubCommonHelper.opfBase(opfPath);
            String navPath = EpubCommonHelper.normalizePath(EpubCommonHelper.resolve(basePath, navHref));
            byte[] navBytes = zip.get(navPath);
            if (navBytes == null) {
                KanLogger.myLogD("Auto mode: Nav file not found, using spine-based");
                return false;
            }

            // Parse TOC and count entries
            String navHtml = EpubCommonHelper.bytesToStringWithXmlGuess(navBytes);
            int tocCount = countTocEntries(navHtml);

            KanLogger.myLogD("Auto mode: Found " + tocCount + " TOC entries");

            // Use TOC if it has a reasonable number of entries (10-1000)
            // Too few (<10) might indicate a broken/malformed TOC
            // Too many (>1000) might indicate overly granular splitting
            if (tocCount >= 10 && tocCount <= 1000) {
                KanLogger.myLogD("Auto mode: TOC looks good, using TOC-based splitting");
                return true;
            } else {
                KanLogger.myLogD(
                        "Auto mode: TOC entry count (" + tocCount + ") outside reasonable range, using spine-based");
                return false;
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, "Auto mode: Error checking TOC, falling back to spine-based");
            return false;
        }
    }

    // Helper methods for TOC detection (using EpubCommonHelper)

    private static EpubGutenbergHelper.OpfInfo parseOpfForNav(byte[] opfXml) throws Exception {
        EpubGutenbergHelper.OpfInfo o = new EpubGutenbergHelper.OpfInfo();
        org.xmlpull.v1.XmlPullParser x = EpubCommonHelper.newPull(opfXml);
        int t;
        while ((t = x.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (t == org.xmlpull.v1.XmlPullParser.START_TAG) {
                String name = x.getName();
                if ("item".equalsIgnoreCase(name)) {
                    String id = EpubCommonHelper.attr(x, "id");
                    String href = EpubCommonHelper.attr(x, "href");
                    String mt = EpubCommonHelper.attr(x, "media-type");
                    String props = EpubCommonHelper.attr(x, "properties");
                    if (id != null && href != null) {
                        o.manifestHref.put(id, href);
                        o.manifestType.put(id, mt != null ? mt : "");
                        if (props != null)
                            o.manifestProps.put(id, props);
                    }
                }
            }
        }
        return o;
    }

    private static String findNavHref(EpubGutenbergHelper.OpfInfo opf) {
        for (java.util.Map.Entry<String, String> e : opf.manifestHref.entrySet()) {
            String id = e.getKey();
            String props = opf.manifestProps.get(id);
            String mt = opf.manifestType.get(id);
            if (props != null && props.toLowerCase(java.util.Locale.ROOT).contains("nav")
                    && mt != null && mt.contains("xhtml")) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Counts TOC entries in the nav HTML for auto mode detection.
     * Returns the number of valid TOC entries (excluding pagelist entries).
     */
    private static int countTocEntries(String navHtml) {
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(navHtml);
            org.jsoup.nodes.Element nav = doc
                    .selectFirst("nav[epub|type=toc], nav[epub\\:type=toc], nav[role=doc-toc]");
            if (nav == null) {
                return 0;
            }

            int count = 0;
            for (org.jsoup.nodes.Element a : nav.select("a[href]")) {
                String title = a.text();
                if (title == null || title.trim().isEmpty()) {
                    continue;
                }
                title = title.trim();

                // Skip pagelist entries like "[12]"
                if (title.matches("\\[\\d+\\]")) {
                    continue;
                }

                String href = a.attr("href");
                if (href == null || href.isEmpty()) {
                    continue;
                }

                String file = href;
                int hash = href.indexOf('#');
                if (hash >= 0) {
                    file = href.substring(0, hash);
                }

                if (file == null || file.isEmpty()) {
                    continue;
                }

                count++;
            }
            return count;
        } catch (Exception e) {
            KanLogger.myLogEE(e, "countTocEntries");
            return 0;
        }
    }

    private static String guessTypeFromPath(String path) {
        String name = new File(path).getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1) : "";
        switch (ext) {
            case "epub":
                return "epub";
            case "fb2":
                return "fb2";
            case "odt":
                return "odt";
            case "docx":
                return "docx";
            // common zipped fb2 variants could be handled later (fb2.zip/fbz) if you add
            // unzip
            default:
                return "epub"; // safe default if you mostly import EPUBs
        }
    }

    private static String readUtf8File(File f) {
        try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(f));
                java.io.InputStreamReader isr = new java.io.InputStreamReader(in,
                        java.nio.charset.StandardCharsets.UTF_8);
                java.io.BufferedReader br = new java.io.BufferedReader(isr, 64 * 1024)) {

            StringBuilder sb = new StringBuilder((int) Math.min(Math.max(f.length(), 128_000L), 2_000_000));
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1)
                sb.append(buf, 0, n);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Preserve paragraphs; do only safe cleanup. */
    private static String cleanTextKeepParagraphs(String raw) {
        if (raw == null)
            return "";
        String s = raw.replace("\r\n", "\n").replace("\r", "\n");
        // Remove control chars except \n and \t
        s = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        // Collapse 3+ blank lines → 2
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    /** From file like "003_chapter-title.txt" → "chapter-title". */
    private static String titleFromFileName(String name) {
        if (name == null)
            return null;
        String base = name;
        int dot = base.lastIndexOf('.');
        if (dot > 0)
            base = base.substring(0, dot);

        // drop leading "###_" index if present (3 or more digits)
        // regex: starts with digits + underscore
        if (base.matches("^\\d{3,}_.*")) {
            int u = base.indexOf('_');
            if (u > 0)
                base = base.substring(u + 1);
        }
        return base;
    }

    /** From file like "003_chapter-title.txt" → "003" (or null if no prefix). */
    private static String leadingIndexFromFileName(String name) {
        if (name == null)
            return null;
        String base = name;
        int dot = base.lastIndexOf('.');
        if (dot > 0)
            base = base.substring(0, dot);

        // check for digits + underscore
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{3,})_").matcher(base);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String toSafeFilename(String s) {
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 80)
            cleaned = cleaned.substring(0, 80).trim();
        if (cleaned.isEmpty())
            cleaned = "chapter";
        return cleaned;
    }

    private static String ensureUnique(Set<String> used, String base) {
        String cand = base;
        int n = 2;
        // case-insensitive uniqueness
        while (used.contains(cand.toLowerCase(Locale.ROOT))) {
            cand = base + " (" + n + ")";
            n++;
        }
        return cand;
    }

    private static void writeUtf8(File file, String text) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            bw.write(text != null ? text : "");
            bw.flush();
        }
    }
}
