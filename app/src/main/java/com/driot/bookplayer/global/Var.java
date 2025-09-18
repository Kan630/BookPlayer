package com.driot.bookplayer.global;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import okhttp3.logging.HttpLoggingInterceptor;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/09/21
 */
public class Var {

    public static final String WORKER_TASK_LABEL_DOWNLOAD = "Download";
    public static final String WORKER_TASK_LABEL_UNZIP = "Unzip";
    public static final String WORKER_TASK_LABEL_SPLIT_M4B = "SplitM4b";
    public static final String WORKER_TASK_LABEL_SPLIT_EPUB = "SplitEpub";
    public static final String WORKER_TASK_LABEL_COPY = "Copy";
    public static final String WORKER_TASK_LABEL_SCAN = "Scan";

    public static final String FOREGROUND_DOWNLOAD_SERVICE_TAG = "download_retry";

    public static final String SOURCE_LOCATION_PODCAST = "podcast";
    public static final String SOURCE_LOCATION_LIBRIVOX = "librivox";

    public static final String PLAY_TYPE_TEXT = "text";
    public static final String PLAY_TYPE_AUDIO = "audio";

    public static final int FALL_BACK_COVER_IMAGE_SIZE_IN_PIXELS = 512;

    public static final int TTS_WPM_IMPORT = 180;

    public static final int MAX_IMAGE_SIZE_KB = 200;

    public static final int PERIODIC_TASK_MANAGER_DELAY_IN_MINUTES = 15;
    public static final boolean FORCE_AUTO_DOWNLOAD_NO_DELAY = false; // for

    public static final HttpLoggingInterceptor.Level HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL = HttpLoggingInterceptor.Level.NONE;

    public static final int LIBRIVOX_API_MAX_RESULTS = 1000;
    public static final int PODCASTINDEXORG_API_MAX_RESULTS_FOR_PODCASTS = 200;
    public static final int PODCASTINDEXORG_API_MAX_RESULTS_FOR_EPISODES_NORMAL_MODE = 100;
    public static final int PODCASTINDEXORG_API_MAX_RESULTS_FOR_EPISODES_REFRESH_MODE = 1000;
    public static final int PODCASTINDEXORG_API_TIME_BETWEEN_PODCAST_CHECK_IN_MIN = 2;

    public static final String PODCASTINDEXORG_API_KEY = "PVULATRUYKDZX26NBGTR";
    public static final String PODCASTINDEXORG_API_SECRET = "ACtMRyawFkg4MxA55y^CH$fE3Dynds4gURfByYXL";
    public static final String PODCAST_SOURCE = "podcastindex.org";

    //public static final int PODCASTINDEXORG_SINCE_DEBUG = 1751716800 ; //5 juillet, 2025
    public static final int PODCASTINDEXORG_SINCE = 0 ; //5 juillet, 2025
    public static final int PODCAST_DETAIL_ANIMATION_COUNT = 5 ;


    public static final String FOLDER_UNZIPPED = "unzipped";
    public static final String FOLDER_DOWNLOAD = "download";
    public static final String FOLDER_IMAGE = "images";
    public static final String PATH_CHECK_AUDIO_FILE_INTERNAL = "com.driot.bookplayer/files/unzipped";

    public static final int  ZIP_SIZE_MAX_COEF = 4;

    public static final String  PATH_AUTOTEST_URL = "https://bookplayer.driot.com/autotest/";

    public static final String  AUTOTEST_FILE_01 = PATH_AUTOTEST_URL + "file_01.zip";
    public static final String  AUTOTEST_FILE_02 = PATH_AUTOTEST_URL + "മലയാളം+عربى+Русский+हिन्दी.zip";
    public static final String  AUTOTEST_FILE_03 = PATH_AUTOTEST_URL + "FrostTonight_librivox.m4b";
    public static final String  AUTOTEST_FILE_04 = PATH_AUTOTEST_URL + "whitefang2_1010_librivox.zip";

    public static final String  WEBSITE_URL = "https://bookplayer.driot.com/";

    public static final int[] SLEEP_PRESET_VALUES = {10, 20, 30, 45, 90, 180};

    public static final Set<String> SUPPORTED_COVER_PICTURE_EXTENSIONS = new HashSet<>(
            Arrays.asList(
                     "jpg"
                    ,"jpeg"
                    ,"png"
                    ,"webp"
                    ,"bmp"
                    //"gif"  // optional if you allow animated covers
            )
    );

    public static final String ONLY_MIME_AUDIO = "audio/";
    public static final Set<String> SUPPORTED_AUDIO_EXTENSIONS = new HashSet<>(
            Arrays.asList(
                    // Common Formats
                    "mp3",   // MPEG-1/2 Audio Layer 3 (universal support)
                    "m4a",   // AAC in MP4 container (Apple/iTunes default)
                    "aac",   // Raw AAC (less common than .m4a)
                    "mp4",   // MP4 container (may contain AAC/ALAC)
                    "m4b",   // Audiobook variant of .m4a
                    "wav",   // Uncompressed PCM/WAVE
                    "ogg",   // Ogg Vorbis (open alternative to MP3)
                    "oga",   // Ogg Audio (legacy, rarely used)
                    "flac",  // Free Lossless Audio Codec (Android 3.1+)
                    "opus"  // Opus (Android 5.0+, efficient for voice/streaming)
/*
                    // MIDI/Synthetic Audio
                    "mid",   // Standard MIDI
                    "midi",  // Alternate MIDI extension
                    "smf",   // Standard MIDI File
                    "xmf",   // Extended MIDI
                    "imy",   // iMelody ringtones (rare)

                    // Voice/Telephony Formats
                    "amr",   // Adaptive Multi-Rate (common for voice recordings)
                    "3ga",   // 3GPP Audio (AMR/AAC in 3GPP container)
                    "awb",   // AMR-WB (Wideband voice)

                    // Legacy/Obscure Formats
                    "mkv",   // Matroska container (may contain AAC/Opus/Vorbis)
                    "aif",   // AIFF (uncompressed, Apple)
                    "aiff",  // AIFF alternate extension
                    "gsm",   // GSM 6.10 (telephony codec, rare)
                    "mka",   // Matroska Audio (rare)
                    "qcp",   // Qualcomm PureVoice (very rare)

                    // Android-Specific
                    "mxmf",  // Mobile XMF (ringtone format)
                    "rtttl", // Ring Tone Text Transfer Language
                    "rtx",   // Ringtone Extension
                    "ota"    // Over-the-Air ringtone

 */
            )
    );

    public static final String ONLY_MIME_VIDEO = "audio/";
    public static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = new HashSet<>(
            Arrays.asList(
                    // Common Formats
                    "mpg",
                    "mpeg",
                    "avi"
            )
    );
    public static final Set<String> SUPPORTED_TEXTUAL_MIMES = new HashSet<>(
            Arrays.asList(
                    "text/plain",
                    "text/html",
                    "application/xhtml+xml",
                    "application/x-fictionbook+xml", // fb2 (sometimes)
                    "application/vnd.oasis.opendocument.text" // odt
            )
    );
    public static final String ONLY_MIME_EBOOK = "application/epub+zip";
    public static final Set<String> SUPPORTED_EBOOK_EXTENSIONS = new HashSet<>(
            Arrays.asList(
                    "epub", // Kobo kepub is still .epub
                    "txt",
                    "fb2"                // FictionBook 2
                    //"html","htm","xhtml", // HTML
                    //"odt"                 // OpenDocument Text
                    )
    );

}
