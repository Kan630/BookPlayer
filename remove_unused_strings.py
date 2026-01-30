"""
Remove unused string/plurals entries from all strings.xml files.
Run find_unused_strings.py first (with fixed regex including period in key names).
Do NOT remove plurals that are used via R.plurals - they are no longer in the unused list.
"""
import os
import re

# Exact list from find_unused_strings.py (regex now includes period so "min." is not in list)
UNUSED_KEYS = [
    "Add_resource_reading_file", "AutoTest_desc", "Auto_Delete_episodes", "Change",
    "Classic_book_view", "DirectDownload_hint", "Download_cancelled", "Download_cancelled_by_user",
    "Download_failed", "Download_paused_due_to_network_policy", "Error_Getting_Ressource_Name_from_Download_Folder",
    "Error_Import_AlreadyImported", "Error_Import_BetterTryNoDownloadFolder", "Error_Import_CannotDeleteSource",
    "Error_Import_CannotDetermineType", "Error_Import_CannotGetParentDir", "Error_Import_CannotParseFile",
    "Error_Import_FilePathKO", "Error_Import_FolderAlreadyImported", "Error_Import_FolderPathKO",
    "Error_Import_NotAnAudio", "Error_Import_NotEnoughMemory_line1", "Error_Import_NotEnoughMemory_line4_1",
    "Error_Import_NotEnoughMemory_line4_2", "Error_Import_NotEnoughMemory_line5_other", "Error_Import_NotEnoughMemory_line5_zip",
    "Error_Import_Split_M4B", "Error_Import_TypeNotSupported", "Error_Import_UnableToUnzip_line1",
    "Error_Import_UnableToUnzip_line2", "Error_Import_access_denied", "Error_Import_computing_folder_duration",
    "Error_Import_track_duration_nofile", "Error_ZikFilePositionOnlyDigits", "Error_checking_file",
    "Export_display_text_export_title", "Export_display_text_loading", "Export_done", "Export_notification_text",
    "Export_notification_title", "Finished", "Generate", "Import_Progress_copying_zip_file",
    "Import_Progress_copying_zip_file_cloud", "Import_Progress_onGoing", "Import_Progress_splitting_m4b_files",
    "Import_finished_with_errors", "ImportedOn", "LibrivoxResultItem_audio_available_since",
    "LibrivoxResultitem_avg_rating", "LibrivoxResultitem_nb_of_reviews", "LibrivoxSearch_hint",
    "LibrivoxSearch_warning", "MB_audios_in_app", "MB_device_memory2", "MB_left_on_device",
    "OnlineSearch_button", "OnlineSearch_desc", "OnlineSearch_hint", "OnlineSearch_title",
    "Pause_wait_interrupted", "Performance", "Please_first_stop_the_player", "PodcastSearch_hint",
    "Podcast_description", "Progress_Heatmap", "RadioSearch_desc", "Radio_settings", "StillExperimental",
    "Surf_the_vast_Internet_Archive_for_audio_files_and_download_some", "Technical_Error", "TextHeaderSearch",
    "ZikFile_RePositioned", "audio_files", "bAddRessource", "bRenameFolder", "bRenameTrack",
    "bSearchExampleText", "bSearchOpenCulture_desc", "bSearchlitteratureaudio_desc", "back", "books",
    "browse_radios", "completed", "copy", "default_", "delay", "deprecated_Ebooks_special",
    "deprecated_Stop_the_player_to_move_playing_tracks", "deprecated__net_status_offline_for",
    "deprecated__net_status_online_for", "deprecated_bOpenMassImport_warning", "deprecated_net_status_offline_for_percent",
    "deprecated_net_status_online_for_percent", "deprecated_option_text_preview_sample", "device_settings",
    "double_click_image_to_get_back_to_text_view", "download_ask_if_not_wifi", "download_ask_if_unmetered",
    "download_cancelled_during_execution", "download_never_ask", "download_unmetered", "download_warning_message_wifi",
    "download_warning_title_wifi", "download_wifi", "error_could_not_check_hash", "error_file_not_found",
    "error_media_already_loaded_sameName", "error_media_already_loaded_samePath_so_changeName", "error_playlist_null",
    "failed", "file_name", "folders_count", "from_previous_request", "hours", "initialization", "librivox_wait_integration",
    "loading_more", "menu_forum", "menu_otherapp", "menu_settings", "menu_synchro", "minutes",
    "no_review", "open_podcasts", "option_app_language_subtitle", "option_auto_play_on_main_player_text",
    "option_automotive_keep_phone_playback_on_car_connect", "option_click_visualizer_playpause_text_01",
    "option_copyzip_local", "option_hide_advanced_options", "option_orientation", "option_orientation_lock",
    "option_radio_renew_url_title", "option_rewind_after_pause_title", "option_show_advanced_options",
    "option_text_preview_label", "option_text_size", "option_timeBeforeSleep_outOfBound", "option_unzip_local",
    "option_visualizer_title", "option_zip", "otherApp_deces_txt", "otherApp_droitpositif_txt",
    "otherApp_scanner_txt", "otherApp_title", "pause_following_error", "permission_record_audio_rationale_01",
    "permission_record_audio_rationale_after_denied", "permission_to_set", "play_mode", "podcast_nb_of_podcast_found",
    "sd_card", "search_nothing_specified_so_trending", "seconds", "section_audio", "section_text", "section_video",
    "source_not_read", "stations", "streaming", "toast_tts_getting_ready", "toast_tts_not_ready", "tracks",
    "trending_radios", "tts_error_engine_generic", "tvSleep", "tvVolume", "tv_CustomSleepDialog_EnterMinutesBeforeSleep",
    "tv_ListeningTimeWithCustomSleep", "url", "warning_beta_test",
]

def remove_entries_from_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    original_len = len(content)
    for key in UNUSED_KEYS:
        pat_str = r'\n?\s*<string name="' + re.escape(key) + r'"(?:\s+[^>]*)?>.*?</string>'
        content = re.sub(pat_str, '', content, flags=re.DOTALL)
        pat_plurals = r'\n?\s*<plurals name="' + re.escape(key) + r'"(?:\s+[^>]*)?>.*?</plurals>'
        content = re.sub(pat_plurals, '', content, flags=re.DOTALL)
    content = re.sub(r'\n{4,}', '\n\n\n', content)
    if len(content) != original_len:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def main():
    base = os.path.join(os.path.dirname(__file__), 'app', 'src', 'main', 'res')
    dirs = ['values', 'values-es', 'values-it', 'values-de', 'values-pt', 'values-ru', 'values-zh', 'values-ar', 'values-hi', 'values-b+fr']
    for d in dirs:
        path = os.path.join(base, d, 'strings.xml')
        if os.path.isfile(path):
            if remove_entries_from_file(path):
                print('Updated:', path)
            else:
                print('No changes:', path)
        else:
            print('Skip (not found):', path)

if __name__ == '__main__':
    main()
