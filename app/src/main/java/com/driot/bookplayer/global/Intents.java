package com.driot.bookplayer.global;

public class Intents {


    public static final String EXTRA_CALLER = "EXTRA_CALLER";
    public static final String EXTRA_FOREGROUND = "EXTRA_FOREGROUND";
    public static final String EXTRA_FOLDER = "extra_folder";
    public static final String EXTRA_FOLDER_ID = "extra_folder_id";
    public static final String EXTRA_ACTIVATE_CHANGE_TRACK_ORDER = "EXTRA_ACTIVATE_CHANGE_TRACK_ORDER";
    public static final String EXTRA_CMD_STOP    = "CMD_STOP";
    public static final String EXTRA_AUTOPLAY    = "extra_autoplay"; // default false
    public static final String ACTION_PLAY_FROM_FOLDER = "com.driot.bookplayer.PLAY_FROM_FOLDER";
    public static final String ACTION_PLAY_FROM_TRACK  = "com.driot.bookplayer.PLAY_FROM_TRACK";
    public static final String EXTRA_TRACK_ID  = "com.driot.bookplayer.EXTRA_TRACK_ID";
    public static final String EXTRA_TRACK_ORDER_NEWEST_FIRST = "com.driot.bookplayer.EXTRA_TRACK_ORDER_NEWEST_FIRST";
    public static final String EXTRA_IS_PODCAST = "com.driot.bookplayer.EXTRA_IS_PODCAST";
    public static final String EXTRA_INDEX     = "extra_index"; // optional, default 0
    public static final String EXTRA_UI_SUPPRESS_MINI = "extra_ui_suppress_mini";

    public static final String CMD_TTS_SET_VOICE = "com.driot.bookplayer.CMD_TTS_SET_VOICE";
    public static final String EXTRA_TTS_VOICE_NAME = "com.driot.bookplayer.EXTRA_TTS_VOICE_NAME";

    public static final String NOTIFICATION_TTS_RANGE = "NOTIFICATION_TTS_RANGE";
    public static final String EXTRA_TTS_START = "EXTRA_TTS_START";
    public static final String EXTRA_TTS_END   = "EXTRA_TTS_END";

    public static final String ACTION_UI_STATE      = "com.driot.bookplayer.action.UI_STATE";
    public static final String ACTION_PING_UI = "com.driot.bookplayer.PING_UI";
    public static final String EXTRA_UI_PLAYING     = "extra_ui_playing";
    public static final String EXTRA_UI_POS         = "extra_ui_pos";
    public static final String EXTRA_UI_DUR         = "extra_ui_dur";
    public static final String EXTRA_UI_TITLE       = "extra_ui_title";
    public static final String EXTRA_UI_SUBTITLE    = "extra_ui_subtitle";
    public static final String EXTRA_UI_COVER       = "extra_ui_cover";
    public static final String EXTRA_UI_TRACK_ID  = "extra_ui_track_id";
    public static final String EXTRA_UI_FOLDER_ID = "extra_ui_folder_id";
    public static final String EXTRA_UI_PODCAST_FEED_ID = "extra_ui_podcast_id";
    public static final String EXTRA_UI_READY     = "extra_ui_ready";
    public static final String EXTRA_UI_PLAYMODE = "extra_ui_tts";
    public static final String EXTRA_UI_PHASE       = "extra_ui_phase";
    public static final String EXTRA_UI_PHASE_MSG   = "extra_ui_phase_msg";

    public static final String PHASE_LOADING_TEXT   = "LOADING_TEXT";   // text extraction / normalization
    public static final String PHASE_WARMING_UP     = "WARMING_UP";     // voice warm-up / prepareAsync
    public static final String PHASE_READY          = "READY";          // engine prepared
    public static final String PHASE_STARTING       = "STARTING";       // start() called, waiting first utterance
    public static final String PHASE_SPEAKING       = "SPEAKING";       // first utt_ started
    public static final String PHASE_ERROR          = "ERROR";          // optional, on early failure
    public static final String PHASE_BUFFERING      = "BUFFERING";      //podcast... streams...
    public static final String PHASE_OFF            = "OFF";


    public static final String ACTION_PLAY_RADIO = "com.driot.bookplayer.action.PLAY_RADIO";
    public static final String ACTION_PLAY_PODCAST = "com.driot.bookplayer.action.PLAY_PODCAST";
    public static final String EXTRA_STREAM_URL  = "extra_stream_url";
    public static final String EXTRA_TITLE       = "extra_title";
    public static final String EXTRA_IMAGE_URL   = "extra_image_url";
    public static final String EXTRA_PODCAST_FEED_ID = "EXTRA_PODCAST_ID";
}
