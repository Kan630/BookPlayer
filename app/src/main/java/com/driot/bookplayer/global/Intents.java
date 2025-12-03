package com.driot.bookplayer.global;

public class Intents {

    public static final String EXTRA_LIBRIVOX_LANGUAGE_ITEM = "EXTRA_LIBRIVOX_LANGUAGE_ITEM";

    public static final String EXTRA_ADD_TO_FOLDER = "EXTRA_ADD_TO_FOLDER";

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
    public static final String EXTRA_ZIKFILE  = "com.driot.bookplayer.EXTRA_ZIKFILE";
    public static final String EXTRA_TRACK_ORDER_NEWEST_FIRST = "com.driot.bookplayer.EXTRA_TRACK_ORDER_NEWEST_FIRST";
    public static final String EXTRA_IS_PODCAST = "com.driot.bookplayer.EXTRA_IS_PODCAST";
    public static final String EXTRA_INDEX     = "extra_index"; // optional, default 0

    public static final String CMD_TTS_SET_VOICE = "com.driot.bookplayer.CMD_TTS_SET_VOICE";
    public static final String EXTRA_TTS_VOICE_NAME = "com.driot.bookplayer.EXTRA_TTS_VOICE_NAME";

    public static final String NOTIFICATION_TTS_RANGE = "NOTIFICATION_TTS_RANGE";
    public static final String EXTRA_TTS_START = "EXTRA_TTS_START";
    public static final String EXTRA_TTS_END   = "EXTRA_TTS_END";

    public static final String ACTION_PING_UI = "com.driot.bookplayer.PING_UI";

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
    public static final String ACTION_PLAY_STREAM = "com.driot.bookplayer.action.PLAY_STREAM";
    public static final String EXTRA_STREAM_URL  = "EXTRA_STREAM_URL";
    public static final String EXTRA_PLAY_MODE  = "EXTRA_PLAY_MODE";
    public static final String EXTRA_TITLE       = "EXTRA_TITLE";
    public static final String EXTRA_IMAGE_URL   = "EXTRA_IMAGE_URL";
    public static final String EXTRA_PODCAST_FEED_ID = "EXTRA_PODCAST_ID";
    public static final String EXTRA_RADIO_STATION_UUID = "EXTRA_RADIO_STATION_UUID";


    public static final String EXTRA_AUDIO_SESSION_ID = "EXTRA_AUDIO_SESSION_ID";

    public static final String CMD_UPDATE_SLEEP = "CMD_UPDATE_SLEEP";
    public static final String EXTRA_CUSTOM_SLEEP_MINUTES = "EXTRA_CUSTOM_SLEEP_MINUTES";
    public static final String CMD_SET_SPEED = "CMD_SET_SPEED";
    public static final String EXTRA_SPEED = "EXTRA_SPEED";

    public static final String CMD_TTS_SET_START = "CMD_TTS_SET_START";
    public static final String EXTRA_TTS_START_OFFSET = "EXTRA_TTS_START_OFFSET";
    public static final String CMD_TTS_GET_TEXT = "CMD_TTS_GET_TEXT";
    public static final String EXTRA_RESULT_RECEIVER = "EXTRA_RESULT_RECEIVER";
    public static final String EXTRA_TTS_TEXT = "EXTRA_TTS_TEXT";

    public static final String EXTRA_BOOK_SOURCE_FOLDER = "EXTRA_BOOK_SOURCE_FOLDER";

}
