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

	public static void handleRadioImages(Context context, long currentTime) {
		if (NetworkHelper.hasInternet(context)) {
			AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
			List<RadioStation> radioStations = db.radioStationDao()
					.getAllWithExternalImagesUnchangedSince24h(currentTime);
			for (RadioStation radioStation : radioStations) {
				myLog("caching favicon for: " + radioStation.name);
				String url = radioStation.favicon;
				String imagePath = ImageHelper.IMAGE_PREFIX_FOR_RADIO_COVERS + radioStation.stationuuid + ".jpg";
				String localPath = ImageHelper.downloadAndVerifyImage(context, url, imagePath, false);

				if (localPath != null) {
					myLogD("Radio favicon downloaded for " + url);
					radioStation.favicon = localPath;
				} else {
					myLogW("Radio favicon download failed or invalid for: " + url);
				}
				radioStation.date_maj = System.currentTimeMillis();
				db.radioStationDao().update(radioStation);
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
		StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, streamUrl, -1, s.stationuuid, s.name, s.favicon,
				caller);
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
		StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, streamUrl, -1, f.stationuuid, f.name, f.favicon,
				caller);
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
			// broadcastUiState("loadAndPlay");
			// main.post(() -> {
			StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, url, -1, null, title, imageUrl, null);
			// if (!ok) { myLogEE(null, "loadAndPlayFromStorage(): playback failed -
			// radio"); }
			// });
			return true;
		} else {
			return false;
		}
	}

	public static boolean backupDataHasRadios(BackupManager.BackupData data) {
		return data.radioStations != null && !data.radioStations.isEmpty();
	}

	public static void addSecondToTimeListened(Context context, int trackId) {
		AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
		db.radioStationDao().addSecondToTimeListened(trackId);
	}


}
