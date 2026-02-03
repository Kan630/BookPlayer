package com.driot.bookplayer.testutil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses LIST_TEST file format:
 * <pre>
 * LoadWay - filepath --- expected nb of tracks - expected img
 * File - fixtures/m4b/sample.m4b --- 6 - true
 * Folder - fixtures/folders/myaudio --- 18 - false
 * </pre>
 */
public final class ListTestParser {

    private static final String SEP = " --- ";
    private static final String PREFIX_FILE = "File - ";
    private static final String PREFIX_FOLDER = "Folder - ";

    public static class TestCase {
        public final String loadWay;   // "File" or "Folder"
        public final String filepath;  // e.g. "fixtures/m4b/sample.m4b"
        public final int expectedNbTracks;
        public final boolean expectedImg;

        public TestCase(String loadWay, String filepath, int expectedNbTracks, boolean expectedImg) {
            this.loadWay = loadWay;
            this.filepath = filepath;
            this.expectedNbTracks = expectedNbTracks;
            this.expectedImg = expectedImg;
        }

        @Override
        public String toString() {
            return formatLine(loadWay, filepath, expectedNbTracks, expectedImg);
        }
    }

    /** Format a single line for LIST_TEST. */
    public static String formatLine(String loadWay, String filepath, int expectedNbTracks, boolean expectedImg) {
        return loadWay + " - " + filepath + SEP + expectedNbTracks + " - " + expectedImg;
    }

    /** Format header line. */
    public static String formatHeader() {
        return "LoadWay - filepath" + SEP + "expected nb of tracks - expected img";
    }

    /** Parse LIST_TEST content. Returns list of TestCase (skips header and empty lines). */
    public static List<TestCase> parse(Reader reader) throws IOException {
        List<TestCase> out = new ArrayList<>();
        try (BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("LoadWay -")) continue;

                int idx = line.indexOf(SEP);
                if (idx < 0) continue;

                String left = line.substring(0, idx).trim();
                String right = line.substring(idx + SEP.length()).trim();

                String loadWay;
                String filepath;
                if (left.startsWith(PREFIX_FILE)) {
                    loadWay = "File";
                    filepath = left.substring(PREFIX_FILE.length()).trim();
                } else if (left.startsWith(PREFIX_FOLDER)) {
                    loadWay = "Folder";
                    filepath = left.substring(PREFIX_FOLDER.length()).trim();
                } else {
                    continue;
                }

                String[] parts = right.split(" - ", 2);
                if (parts.length < 2) continue;
                int expectedNbTracks = parseInt(parts[0].trim(), -1);
                boolean expectedImg = "true".equalsIgnoreCase(parts[1].trim());

                out.add(new TestCase(loadWay, filepath, expectedNbTracks, expectedImg));
            }
        }
        return out;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
