package com.driot.bookplayer.global;

import com.driot.bookplayer.BuildConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import okhttp3.logging.HttpLoggingInterceptor;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/09/21
 */
public class Var {

    public static final int HEATMAP_PROGRESSBAR_BUCKET_SIZE = 400;

    public static final int PERIODIC_TASK_MANAGER_INITIAL_DELAY_IN_SECONDS = 15;

    public static final int RADIO_STATION_MAX_DUPLICATES = 3; // change value in strings...
    // Example blacklist
    public static final Set<String> RADIO_STATION_BLACKLIST = new HashSet<>(Arrays.asList(
            //"Abdulbasit Abdulsamad",
            "Spam Station 1",
            "Fake Radio"
    ));

    public static final String IMPORT_STATUS_IDLE      = "IDLE";
    public static final String IMPORT_STATUS_QUEUED    = "QUEUED";
    public static final String IMPORT_STATUS_RUNNING   = "RUNNING";
    public static final String IMPORT_STATUS_PAUSED    = "PAUSED";
    public static final String IMPORT_STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String IMPORT_STATUS_FAILED    = "FAILED";
    public static final String IMPORT_STATUS_CANCELLED = "CANCELLED";

    public static final String SHOULD_NOT_HAPPEN = "should not happen";

    public static final String WORKER_TASK_LABEL_DOWNLOAD = "Download";
    public static final String WORKER_TASK_LABEL_DECOMPRESS = "Decompress";
    public static final String WORKER_TASK_LABEL_SPLIT_M4B = "SplitM4b";
    public static final String WORKER_TASK_LABEL_SPLIT_EBOOK = "SplitEbook";
    public static final String WORKER_TASK_LABEL_COPY = "Copy";
    public static final String WORKER_TASK_LABEL_SCAN = "Scan";
    public static final String WORKER_MASS_IMPORT = "MassImport";

    public static final double PLAY_SPEED_MIN = 0.5;
    public static final double PLAY_SPEED_MAX = 3.0;
    public static final double PLAY_SPEED_STEP = 0.05;
    public static final double START_AT_ZERO_IF_TRACK_AT_END_BUFFER_DELAY_IN_MS = 500;

    public static final int[][] REWIND_AFTER_PAUSE = {  // stopped listening since (in min)  ,  rewind delay (in ms)
            {2, 3000},
            {30, 5000},
            {60 * 12, 10000},
            {60 * 36, 15000},
            {60 * 24 * 3, 20000},
            {60 * 24 * 30, 30000},
    };

    public static final String USER_AGENT_BOOKPLAYER = "BookPlayer/1.0 (Android)";

    public static final String SOURCE_LOCATION_PODCAST = "podcast";
    public static final String SOURCE_LOCATION_LIBRIVOX = "librivox";
    public static final String SOURCE_LOCATION_EBOOK_GUTENDEX = "ebook_gutendex";

    public static final String PLAY_TYPE_TEXT = "text";
    public static final String PLAY_TYPE_AUDIO = "audio";

    public static final String PLAY_MODE_BOOK = "book";
    public static final String PLAY_MODE_TTS = "tts";
    public static final String PLAY_MODE_RADIO = "radio";
    public static final String PLAY_MODE_PODCAST = "podcast";

    public static final String REPO_TYPE_AUDIOBOOK = "audiobook";
    public static final String REPO_NAME_LIBRIVOX = "librivox";
    public static final int RADIO_LIST_MAX_CARD_ITEM = 500; //WARNING, if you change that, also change the scheduled worker in cloudfare...
    public static final int LIBRIVOX_LIST_MAX_CARD_ITEM = 400;

    public static final int FALL_BACK_COVER_IMAGE_SIZE_IN_PIXELS = 512;
    public static final int GRID_LAYOUT_SPACER = 4; //dp between cards
    public static final int GRID_LAYOUT_SPACER_RADIO = 2;

    public static final int TTS_WPM_IMPORT = 180;

    public static final int MAX_IMAGE_SIZE_KB = 200;

    public static final int PERIODIC_TASK_MANAGER_DELAY_IN_MINUTES = 15;
    public static final boolean FORCE_AUTO_DOWNLOAD_NO_DELAY = false; // for

    public static final HttpLoggingInterceptor.Level HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL =
            BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BODY
                    : HttpLoggingInterceptor.Level.NONE;

    public static final int LIBRIVOX_API_MAX_RESULTS = 1000;
    public static final int LIBRIVOX_API_MIN_RESULTS = 20;
    public static final int LIBRIVOX_API_PAGE_SIZE = 100;
    public static final int PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_PODCASTS = 1000;
    public static final int PODCAST_INDEX_ORG_API_MIN_RESULTS_FOR_PODCASTS = 20;
    public static final int PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_EPISODES_NORMAL_MODE = 100;
    public static final int PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_EPISODES_REFRESH_MODE = 1000;
    public static final int PODCAST_INDEX_ORG_API_TIME_BETWEEN_PODCAST_CHECK_IN_MIN = 2;

    public static final String PODCAST_SOURCE = "podcastindex.org";
    public static final String DEFAULT_RADIO_MIRROR = "https://de1.api.radio-browser.info/";  //https://fi1.api.radio-browser.info";  //"https://radio.driot.com";

    //public static final int PODCAST_INDEX_ORG_SINCE_DEBUG = 1751716800 ; //5 juillet, 2025
    public static final int PODCAST_INDEX_ORG_SINCE = 0 ; //5 juillet, 2025
    public static final int PODCAST_DETAIL_ANIMATION_COUNT = 5 ;


    public static final String FOLDER_UNZIPPED = "unzipped";
    public static final String FOLDER_DOWNLOAD = "download";
    public static final String FOLDER_IMAGE = "images";
    public static final String FOLDER_CACHED_IMAGE = "cached_images";
    public static final String PATH_CHECK_AUDIO_FILE_INTERNAL_PROD = "com.driot.bookplayer/files/unzipped";
    public static final String PATH_CHECK_AUDIO_FILE_INTERNAL_DEBUG = "com.driot.bookplayer.debug/files/unzipped";

    public static final int ZIP_SIZE_MAX_COEF = 3;
    public static final int M4B_SIZE_MAX_COEF = 3;

    public static final String  PATH_AUTOTEST_URL = "https://bookplayer.driot.com/autotest/";

    public static final String AUTOTEST_FILE_01 = PATH_AUTOTEST_URL + "file_01.zip";
    public static final String AUTOTEST_FILE_02 = PATH_AUTOTEST_URL + "മലയാളം+عربى+Русский+हिन्दी.zip";
    public static final String AUTOTEST_FILE_03 = PATH_AUTOTEST_URL + "FrostTonight_librivox.m4b";
    public static final String AUTOTEST_FILE_04 = PATH_AUTOTEST_URL + "whitefang2_1010_librivox.zip";

    public static final String VISUALIZER_TYPE_LEGACY = "LEGACY";
    public static final String VISUALIZER_TYPE_BARS = "BARS";
    public static final String VISUALIZER_TYPE_RADIAL = "RADIAL";
    public static final String VISUALIZER_TYPE_WAVE = "WAVE";

    public static final String  WEBSITE_URL = "https://bookplayer.driot.com/";

    public static final int[] SLEEP_PRESET_VALUES = {10, 20, 30, 45, 90, 180};

    public static final Set<String> SUPPORTED_COMPRESSED_FILE_EXTENSIONS = new HashSet<>(
            Arrays.asList(
                    "zip"
                    ,"7z"
                    ,"tar"
                    ,"tgz"
                    ,"tbz2"
                    ,"txz"
                    //,"tar.bz2"
                    //,"tar.xz"
            )
    );

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

    // --- Image files ---
    public static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = new HashSet<>(
            Arrays.asList(
                    "jpg",
                    "jpeg",
                    "png",
                    "webp",
                    "bmp"
                    ,"gif"  // optional if you allow animated covers
            )
    );
    public static final Set<String> SUPPORTED_IMAGE_MIMES = new HashSet<>(
            Arrays.asList(
                    "image/jpeg",
                    "image/png",
                    "image/webp",
                    "image/bmp"
                    // "image/gif"
            )
    );

    // --- Audio files ---
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
                    "opus",  // Opus (Android 5.0+, efficient for voice/streaming)

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
            )
    );
    public static final Set<String> SUPPORTED_AUDIO_MIMES = new HashSet<>(
            Arrays.asList(
                    "audio/mpeg",
                    "audio/mp4",
                    "audio/aac",
                    "audio/x-m4a",
                    "audio/wav",
                    "audio/x-wav",
                    "audio/ogg",
                    "audio/flac",
                    "audio/opus"
            )
    );

    // --- Video files ---
    public static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = new HashSet<>(
            Arrays.asList(
                    "mpg",
                    "mpeg",
                    "avi"
            )
    );
    public static final Set<String> SUPPORTED_VIDEO_MIMES = new HashSet<>(
            Arrays.asList(
                    "video/mpeg",
                    "video/x-msvideo" // AVI
            )
    );

    // --- Ebooks/Text files ---
    public static final Set<String> SUPPORTED_EBOOK_EXTENSIONS = new HashSet<>(
            Arrays.asList(
                    "epub",  // Kobo kepub is still .epub
                    "txt",
                    "fb2",   // FictionBook 2
                    "odt"    // OpenDocument Text
                    // "html","htm","xhtml"
            )
    );
    public static final Set<String> SUPPORTED_EBOOK_MIMES = new HashSet<>(
            Arrays.asList(
                    "application/epub+zip",               // epub
                    "text/plain",                         // txt
                    "text/html",                          // html
                    "application/xhtml+xml",              // xhtml
                    "application/x-fictionbook+xml",      // fb2
                    "application/vnd.oasis.opendocument.text" // odt
            )
    );

}
