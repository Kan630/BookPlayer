package com.driot.bookplayer.global;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/09/21
 */
public class Var {

    public static final String WORKER_TASK_LABEL_DOWNLOAD = "Download";
    public static final String WORKER_TASK_LABEL_UNZIP = "Unzip";
    public static final String WORKER_TASK_LABEL_SPLIT = "Split";
    public static final String WORKER_TASK_LABEL_COPY = "Copy";
    public static final String WORKER_TASK_LABEL_SCAN = "Scan";

    public static final String FOREGROUND_DOWNLOAD_SERVICE_TAG = "download_retry";

    public static final String SOURCE_LOCATION_PODCAST = "podcast";
    public static final String SOURCE_LOCATION_LIBRIVOX = "librivox";

    public static final int MAX_IMAGE_SIZE_KB = 200;

    public static final int LIBRIVOX_API_MAX_RESULTS = 100;
    public static final int PODCASTINDEXORG_API_MAX_RESULTS = 100;

    public static final int PODCASTINDEXORG_MAX_PODCAST_AUTO_DOWNLOAD = 5;
    public static final int PODCASTINDEXORG_MAX_EPISODE_AUTO_DOWNLOAD = 5;
    public static final int PODCASTINDEXORG_API_MIN_TIME_BETWEEN_AUTO_CHECK_IN_MIN = 60;

    public static final String PODCASTINDEXORG_API_KEY = "PVULATRUYKDZX26NBGTR";
    public static final String PODCASTINDEXORG_API_SECRET = "ACtMRyawFkg4MxA55y^CH$fE3Dynds4gURfByYXL";
    public static final String PODCAST_SOURCE = "podcastindex.org";

    //public static final int PODCASTINDEXORG_SINCE_DEBUG = 1751716800 ; //5 juillet, 2025
    public static final int PODCASTINDEXORG_SINCE_DEBUG = 0 ; //5 juillet, 2025
    public static final int PODCAST_DETAIL_ANIMATION_COUNT = 3 ;


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

}
