package com.driot.bookplayer.services.archives;

import java.io.File;

public interface ArchiveExtractor {
    void extract(File archive, File dest, ProgressSink progress, CancelChecker cancel) throws Exception;
}
