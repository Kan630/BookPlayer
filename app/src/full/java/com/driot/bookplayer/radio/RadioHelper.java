package com.driot.bookplayer.radio;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.db.RadioStationDao;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.player.StartPlayHelper;

import java.io.File;
import java.util.List;

public class RadioHelper {
	
	public static void handleRadioImages(Context context) {
		if (NetworkHelper.hasInternet(context)) {
			AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
			List<RadioStation> radioStations = db.radioStationDao().getAllWithExternalImagesUnchangedSince24h(System.currentTimeMillis());
			for (RadioStation radioStation : radioStations) {
				String url = radioStation.favicon;
				String imagePath = ImageHelper.IMAGE_PREFIX_FOR_RADIO_COVERS + radioStation.stationuuid + ".jpg";
				String localPath = null;
				localPath = ImageHelper.downloadAndMaybeCompressImage(context, url, imagePath, false);
				if (localPath != null) {
					File f = new File(localPath);
					if (f.exists() && f.length() > 0L) {
						// OK, non-empty file → persist local path
						radioStation.favicon = localPath;
						radioStation.date_maj = System.currentTimeMillis();
						db.radioStationDao().update(radioStation);
					} else {
						// 0 KB or missing → treat as failure, clean up
						myLogW("Radio favicon download failed or empty (" + f.length() + " bytes) for " + url);
						if (f.exists() && f.length() == 0L) {
							try {
								myLog("deleting bad file, success=" + f.delete());
							} catch (Exception ignored) {
							}
						}
						radioStation.date_maj = System.currentTimeMillis();
						db.radioStationDao().update(radioStation);
						// keep old favicon URL in DB so Glide can still try remote
					}
				}
			}
		}
	}			
	
    public static void handleDeepLink(Context context, Uri data) {
		String url = data.getQueryParameter("url");
		String uuid = data.getQueryParameter("uuid");
		myLog("url=[" + url + "] - uuid=[" + uuid + "]");

		if (uuid != null) {
			Intent i = new Intent(context, RadioStationActivity.class);
			i.putExtra(Intents.EXTRA_STATION_UUID, uuid);
			i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			context.startActivity(i);
		}
		if (url != null) {
			playRadioFromUuidAndUrl(context, uuid, url, "DeepLink");
		}
	}

    public static void initRadioBrowserServiceFactory(Context context) {
        RadioBrowserServiceFactory.init(context.getApplicationContext());
    }

	public static void playRadioFromUuidAndUrl(Context context, String uuid, String streamUrl, String caller) {
		Station station = new Station();
		station.stationuuid = uuid;
		station.url = streamUrl;
		station.name = "shared station";
		onRadioClick(context, station, streamUrl, caller);
	}

	public static void onRadioClick(Context context, Station s, String streamUrl, String caller) {
		myLogI("onRadioClick()");
		StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, streamUrl, -1, s.stationuuid, s.name, s.favicon, caller);
		// update DB
		if (s.stationuuid == null) {
			myLogEE(null, "onRadioClick() - null uuid - caller=" + caller);
			return;
		}
		final Station station = s;
		AppDatabase.databaseWriteExecutor.execute(() -> {
			RadioStationDao dao = AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao();
			RadioStation radioStation = dao.findByUuid(station.stationuuid);
			if (radioStation == null) {
				radioStation = RadioStation.fromStation(station, streamUrl);
				radioStation.date_last_played = System.currentTimeMillis();
				dao.insert(radioStation);
			} else {
				radioStation.url_resolved = streamUrl;
				radioStation.date_maj = System.currentTimeMillis();
				radioStation.date_last_played = System.currentTimeMillis();
			}
			AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao().update(radioStation);
		});
	}

	public static void onRadioFavoriteClick(Context context, RadioFavoriteItem f, String streamUrl, String caller) {
		StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, streamUrl, -1, f.stationuuid, f.name, f.favicon, caller);
		// update DB
		AppDatabase.databaseWriteExecutor.execute(() -> {
			RadioStationDao dao = AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao();
			RadioStation radioStation = dao.findByUuid(f.stationuuid);
			if (radioStation == null) {
				myLogE("this should not happens, radio station should already been in Favorites");
				// dao.insert(f)...
			} else {
				radioStation.url_resolved = streamUrl;
				radioStation.date_maj = System.currentTimeMillis();
				radioStation.date_last_played = System.currentTimeMillis();
			}
			AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao().update(radioStation);
		});
	}

	 public static boolean playStreamIfKnownRadio(Context context, String url) {
		 
            RadioStation rs = AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao().getFromUrl(url);
            if (rs != null) {
                String title = rs.name;
                String imageUrl = rs.favicon;
                //broadcastUiState("loadAndPlay");
                //main.post(() -> {
                StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, url, -1, null, title, imageUrl, null);
                    //if (!ok) { myLogEE(null, "loadAndPlayFromStorage(): playback failed - radio"); }
                //});
                return true;
            } else {
				return false;
			}
	 }

    public static boolean backupDataHasRadios(BackupManager.BackupData data) {
        return data.radioStations != null && !data.radioStations.isEmpty();
    }


	
}

