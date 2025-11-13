package com.driot.bookplayer.services.archives;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;

public final class ArchiveDispatch {

    public static ArchiveExtractor forFile(File f) throws Exception {
        String n = f.getName().toLowerCase(Locale.ROOT);

        // quick extension check first
        if (n.endsWith(".zip")) return new ZipExtractor();
        if (n.endsWith(".tar") || n.endsWith(".tgz") || n.endsWith(".tar.gz")
                || n.endsWith(".tbz2") || n.endsWith(".tar.bz2")
                || n.endsWith(".txz") || n.endsWith(".tar.xz")) return new TarExtractor();
        if (n.endsWith(".7z")) return new SevenZExtractor();
        //if (n.endsWith(".rar")) return new RarExtractor();

        // fallback to magic sniff (first few bytes)
        try (InputStream in = new FileInputStream(f)) {
            byte[] magic = new byte[8];
            int r = in.read(magic);
            if (r >= 4 && magic[0]==0x50 && magic[1]==0x4B && magic[2]==0x03 && magic[3]==0x04) return new ZipExtractor(); // PK..
            if (r >= 6 && magic[0]==0x1F && magic[1]==(byte)0x8B) return new TarExtractor(); // gzip'd tar (we’ll auto-detect compressor)
            if (r >= 6 && magic[0]==0x37 && magic[1]==0x7A && magic[2]==(byte)0xBC && magic[3]==(byte)0xAF && magic[4]==0x27 && magic[5]==0x1C) return new SevenZExtractor();
            //if (r >= 7 && magic[0]==0x52 && magic[1]==0x61 && magic[2]==0x72 && magic[3]==0x21 && magic[4]==0x1A && magic[5]==0x07) return new RarExtractor();
        }
        throw new IllegalArgumentException("Unsupported archive type: " + f);
    }

    private ArchiveDispatch() {}
}
