// app/src/androidTest/java/com/driot/bookplayer/testutil/FixtureTestUtils.java
package com.driot.bookplayer.testutil;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FixtureTestUtils {

    private FixtureTestUtils() {}

    public static final String TAG = "FixtureTest";

    // Reuse your interface via a tiny delegate
    private static final LogSupport log = new LogSupport() {
        @Override public String tag() { return TAG; }
    };

    /** Supprime récursivement un dossier/fichier. */
    public static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursively(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Copie un asset (ex: "fixtures/zip/basic.zip") vers un fichier cible. */
    public static void copyAssetToFile(@NonNull Context ctx, @NonNull String assetPath, @NonNull File out) throws Exception {
        AssetManager am = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        try (InputStream in = am.open(assetPath); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        }
    }

    /** Calcule SHA-256 de tous les fichiers sous root (clé = chemin relatif type "dir/file"). */
    public static Map<String, String> computeHashesSHA256(@NonNull File root) throws Exception {
        Map<String, String> out = new HashMap<>();
        walkAndHash(root, root, out);
        return out;
    }

    private static void walkAndHash(File root, File current, Map<String, String> out) throws Exception {
        File[] kids = current.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) {
                walkAndHash(root, k, out);
            } else {
                String rel = relativize(root, k);
                out.put(rel, sha256Hex(k));
            }
        }
    }

    private static String relativize(File root, File file) throws Exception {
        String rootUri = root.getCanonicalFile().toURI().toString();
        String fileUri = file.getCanonicalFile().toURI().toString();
        String rel = fileUri.substring(rootUri.length());
        return rel.replace('\\', '/');
    }

    private static String sha256Hex(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file);
             DigestInputStream dis = new DigestInputStream(in, md)) {
            byte[] buf = new byte[8192];
            while (dis.read(buf) != -1) { /* feed digest */ }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Lit le flag INIT depuis les arguments d’instrumentation: `-e INIT true` */
    public static boolean isInitMode() {
        String v = InstrumentationRegistry.getArguments().getString("INIT", "false");
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    /** Log lisible des hashs (pour copier/coller dans tes constantes). */
    public static void logHashes(Map<String, String> hashes) {
        log.myLogD("==== BEGIN HASHES (SHA-256) ====");
        hashes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> log.myLog(e.getKey() + " = " + e.getValue()));
        log.myLogD("==== END HASHES ====");
    }

    // ===================== NEW: Folder fingerprint =====================

    /** Line record for the canonical listing: "relPath\t<size>" (path NFC normalized). */
    private static String canonicalRecord(String relPath, long size) {
        // Normalize Unicode to NFC for deterministic hashing across filesystems
        String nfc = Normalizer.normalize(relPath, Normalizer.Form.NFC);
        return nfc + "\t" + size;
    }

    /** Returns the canonical listing (sorted) of all files under root (no dirs). */
    public static List<String> buildCanonicalListing(@NonNull File root) throws Exception {
        List<String> rows = new ArrayList<>();
        collectListing(root, root, rows);
        Collections.sort(rows); // lexicographic order -> stable
        return rows;
    }

    private static void collectListing(File root, File cur, List<String> rows) throws Exception {
        File[] kids = cur.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) {
                collectListing(root, k, rows);
            } else {
                String rel = relativize(root, k);
                rows.add(canonicalRecord(rel, k.length()));
            }
        }
    }

    /** Compute a single SHA-256 over the canonical listing (UTF-8, LF separators). */
    public static String computeFolderStructureHash(@NonNull File root) throws Exception {
        List<String> rows = buildCanonicalListing(root);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String row : rows) {
            md.update(row.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n'); // explicit LF separator
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Logs the canonical listing + the final folder hash. Useful in INIT mode. */
    public static void logFolderListingAndHash(@NonNull File root) throws Exception {
        List<String> rows = buildCanonicalListing(root);
        log.myLog("==== BEGIN FOLDER LISTING (relPath\\tsize) ====");
        for (String r : rows) log.myLogI(r);
        String hash = computeFolderStructureHash(root);
        log.myLogI("FOLDER_HASH_SHA256 = " + hash);
        log.myLog("==== END FOLDER LISTING ====");
    }
}
