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

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

		if (uuid == null || url == null) {
			myLogEE(null, "handle deepLink radio, missing uuid or url");
			return;
		}
		Intent i = new Intent(context, RadioStationActivity.class);
		i.putExtra(Intents.EXTRA_STATION_UUID, uuid);
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(i);
		playRadioFromUuidAndUrl(context, uuid, url, "DeepLink");
	}

	public static void initRadioBrowserServiceFactory(Context context) {
		RadioBrowserServiceFactory.init(context.getApplicationContext());
	}

	public static void playRadioFromUuidAndUrl(Context context, String uuid, String streamUrl, String caller) {
		AppDatabase.databaseWriteExecutor.execute(() -> {
			RadioStationDao dao = AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao();
			RadioStation radioStation = dao.findByUuid(uuid);
			if (radioStation == null) {
				// Shell station if not in DB and no API data yet
				radioStation = new RadioStation();
				radioStation.stationuuid = uuid;
				radioStation.name = "Unknown Station";
				radioStation.url = streamUrl;
				radioStation.url_resolved = streamUrl;
				radioStation.date_added = System.currentTimeMillis();
				radioStation.date_maj = radioStation.date_added;
				radioStation.id = dao.insert(radioStation);
			} else {
				radioStation.url_resolved = streamUrl;
				radioStation.date_maj = System.currentTimeMillis();
				dao.update(radioStation);
			}
			play(context, radioStation, streamUrl, caller);
			fetchAndUpsertStation(context, uuid);
		});
	}

	public static void fetchAndUpsertStation(Context context, String stationUuid) {
		RadioBrowserRepository repo = new RadioBrowserRepository(
				context.getApplicationContext(),
				false,
				Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
		repo.searchByUuid(stationUuid, new Callback<List<ApiStation>>() {
			@Override
			public void onResponse(Call<List<ApiStation>> call, Response<List<ApiStation>> response) {
				myLogD("refresh station from API - get details - success = " + response.code());
				if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
					ApiStation apiStation = response.body().get(0);
					AppDatabase.databaseWriteExecutor.execute(() -> {
						upsertFromApi(context.getApplicationContext(), apiStation, apiStation.url_resolved);
					});
				}
			}

			@Override
			public void onFailure(Call<List<ApiStation>> call, Throwable t) {
				myLogW("fetchAndUpsertStation failed: " + t);
			}
		});
	}

	public static void play(Context context, RadioStation rs, String streamUrl, String caller) {
		myLogI("play() id=" + rs.id + " name=" + rs.name);
		StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, streamUrl,
				(int) rs.id, rs.name, rs.favicon, caller);
		updatePlayed(context, rs);
	}

	public static void play(Context context, ApiStation s, String streamUrl, String caller) {
		AppDatabase.databaseWriteExecutor.execute(() -> {
			RadioStation rs = upsertFromApi(context, s, streamUrl);
			play(context, rs, streamUrl, caller);
		});
	}

	public static RadioStation upsertFromApi(Context context, ApiStation apiStation, String streamUrl) {
		RadioStationDao dao = AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao();
		RadioStation dbStation = dao.findByUuid(apiStation.stationuuid);

		if (dbStation == null) {
			dbStation = RadioStation.fromStation(apiStation, streamUrl);
			long insertedId = dao.insert(dbStation);
			dbStation.id = insertedId;
		} else {
			dbStation.url_resolved = streamUrl != null ? streamUrl : apiStation.url_resolved;
			dbStation.name = apiStation.name;
			dbStation.url = apiStation.url;
			dbStation.codec = apiStation.codec;
			dbStation.bitrate = apiStation.bitrate;
			dbStation.hls = apiStation.hls;

			// Protect local favicon with deep comparison
			boolean isLocalFavicon = dbStation.favicon != null && !dbStation.favicon.startsWith("http");
			if (isLocalFavicon) {
				if (apiStation.favicon != null && !apiStation.favicon.isEmpty()) {
					byte[] remoteBytes = com.driot.bookplayer.helpers.NetworkHelper
							.fetchBytesWithHttpsFallbackForImage(apiStation.favicon);
					if (remoteBytes != null) {
						byte[] localBytes = null;
						try {
							java.io.File localFile = new java.io.File(dbStation.favicon);
							if (localFile.exists()) {
								java.io.InputStream in = new java.io.FileInputStream(localFile);
								java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
								byte[] buf = new byte[8192];
								int n;
								while ((n = in.read(buf)) != -1) {
									out.write(buf, 0, n);
								}
								in.close();
								localBytes = out.toByteArray();
							}
						} catch (Exception e) {
							myLogW("Failed to read local favicon for comparison: " + e);
						}

						if (localBytes != null) {
							String remoteHash = com.driot.bookplayer.helpers.ImageHelper.shortHash(remoteBytes);
							String localHash = com.driot.bookplayer.helpers.ImageHelper.shortHash(localBytes);
							if (!remoteHash.equals(localHash)) {
								myLogW("Favicon changed (content diff), updating to remote: " + apiStation.favicon);
								dbStation.favicon = apiStation.favicon;
							}
						} else {
							dbStation.favicon = apiStation.favicon;
						}
					}
				}
			} else {
				dbStation.favicon = apiStation.favicon;
			}

			if (apiStation.favicon != null && apiStation.favicon.startsWith("http")) {
				dbStation.imageOriginalUrl = apiStation.favicon;
			}

			dbStation.country = apiStation.country;
			dbStation.countrycode = apiStation.countrycode;
			dbStation.language = apiStation.language;
			dbStation.tags = apiStation.tags;
			dbStation.clickcount = apiStation.clickcount;
			dbStation.lastcheckok = apiStation.lastcheckok;
			dbStation.state = apiStation.state;
			dbStation.iso_3166_2 = apiStation.iso_3166_2;
			dbStation.votes = apiStation.votes;
			dbStation.homepage = apiStation.homepage;
			dbStation.date_maj = System.currentTimeMillis();

			dao.update(dbStation);
		}
		return dbStation;
	}

	public static void updatePlayed(Context context, RadioStation rs) {
		AppDatabase.databaseWriteExecutor.execute(() -> {
			rs.date_last_played = System.currentTimeMillis();
			AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao().update(rs);
		});
	}

	public static boolean playStreamIfKnownRadio(Context context, String url) {
		RadioStation rs = AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao().getFromUrl(url);
		if (rs != null) {
			StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, url,
					(int) rs.id, rs.name, rs.favicon, null);
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
