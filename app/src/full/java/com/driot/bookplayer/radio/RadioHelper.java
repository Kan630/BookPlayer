package com.driot.bookplayer.radio;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.core.content.FileProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.db.RadioStationDao;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FlagHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.ShareHelper;
import com.driot.bookplayer.player.MediaService;
import com.driot.bookplayer.player.StartPlayHelper;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioHelper {

	private static final Set<String> activeFetches = Collections.synchronizedSet(new HashSet<>());

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
		if (activeFetches.contains(stationUuid)) {
			myLogD("fetchAndUpsertStation: fetch already in progress for " + stationUuid);
			return;
		}

		AppDatabase.databaseWriteExecutor.execute(() -> {
			RadioStationDao dao = AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao();
			RadioStation rs = dao.findByUuid(stationUuid);

			// Freshness check: 5 minutes
			if (rs != null && rs.date_maj > System.currentTimeMillis() - Var.RADIO_REFRESH_FOR_STATION_ACTIVITY_IN_MS
					&& !"Unknown Station".equals(rs.name)) {
				myLogD("fetchAndUpsertStation: metadata is fresh for " + rs.name);
				return;
			}

			activeFetches.add(stationUuid);

			RadioBrowserRepository repo = new RadioBrowserRepository(
					context.getApplicationContext(),
					false,
					Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
			repo.searchByUuid(stationUuid, new Callback<List<ApiStation>>() {
				@Override
				public void onResponse(Call<List<ApiStation>> call, Response<List<ApiStation>> response) {
					activeFetches.remove(stationUuid);
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
					activeFetches.remove(stationUuid);
					myLogW("fetchAndUpsertStation failed: " + t);
				}
			});
		});
	}

	public static void play(Context context, RadioStation rs, String streamUrl, String caller) {
		myLog("play() id=" + rs.id + " name=" + rs.name);
		if (rs.id <= 0)
			myLogE("null radio ID");
		StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, streamUrl,
				rs.id, rs.name, rs.favicon, caller);
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
			dbStation = RadioStation.fromApiStation(apiStation, streamUrl);
			dbStation.id = dao.insert(dbStation);
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
		// Resolve and persist favicon if missing
		ImageHelper.resolveAndPersistFavicon(context, dbStation);

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
					rs.id, rs.name, rs.favicon, null);
			return true;
		} else {
			return false;
		}
	}

	public static boolean backupDataHasRadios(BackupManager.BackupData data) {
		return data.radioStations != null && !data.radioStations.isEmpty();
	}

	public static void addSecondToTimeListened(Context context, long trackId) {
		AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
		db.radioStationDao().addSecondToTimeListened(trackId);
	}

	public static void shareRadioStation(Context context, String uuid) {
		Context appCtx = context.getApplicationContext();
		AppDatabase.databaseReadExecutor.execute(() -> {
			RadioStation rs = AppDatabase.getInstance(appCtx).radioStationDao().findByUuid(uuid);
			if (rs == null) {
				myLogE("ShareHelper.shareRadioStation: station not found in DB");
				return;
			}

			String radioLink = "https://bookplayer.driot.com/share/radio?url=" + Uri.encode(rs.url_resolved)
					+ "&uuid=" + Uri.encode(uuid);

			String sharedMessageBody = appCtx.getString(R.string.share_radio_body) + ": \n\n" + rs.name + "\n\n"
					+ radioLink;
			String sharedMessageHead = appCtx.getString(R.string.share_radio_head);

			// Optional image sharing
			Uri imageUri = null;
			if (rs.favicon != null && !rs.favicon.isEmpty()) {
				if (rs.favicon.startsWith("http")) {
					// Download if it's a URL
					String fileName = "share_radio_" + rs.stationuuid + ".jpg";
					String localPath = ImageHelper.downloadAndVerifyImage(appCtx, rs.favicon, fileName, true);
					if (localPath != null) {
						try {
							imageUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".FileProvider",
									new File(localPath));
						} catch (Exception e) {
							myLogEE(e, "shareRadioStation: FileProvider failed for downloaded " + localPath);
						}
					}
				} else if (rs.favicon.startsWith("content://")) {
					// Already a content URI
					imageUri = Uri.parse(rs.favicon);
				} else {
					// Assume it's a local absolute path
					File file = new File(rs.favicon);
					if (file.exists()) {
						try {
							imageUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".FileProvider",
									file);
						} catch (Exception e) {
							myLogEE(e, "shareRadioStation: FileProvider failed for local file " + rs.favicon);
						}
					} else {
						myLogW("shareRadioStation: local image file not found: " + rs.favicon);
					}
				}
			}

			ShareHelper.shareContent(context, sharedMessageBody, sharedMessageHead, imageUri);
		});
	}

	// ---- Android Auto Helpers ----

	public static boolean hasFavorites(Context context) {
		return AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao().countFavorites() > 0;
	}

	public static List<MediaBrowserCompat.MediaItem> getFavoriteRadios(Context context) {
		List<RadioStation> favorites = AppDatabase.getDatabase(context.getApplicationContext())
				.radioStationDao().getFavoritesByLastPlayed();
		List<MediaBrowserCompat.MediaItem> items = new java.util.ArrayList<>();

		for (RadioStation rs : favorites) {
			MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder()
					.setMediaId("radio:" + rs.stationuuid)
					.setTitle(rs.name)
					.setSubtitle(rs.tags);

			// Icon
			if (rs.favicon != null) {
				Bitmap icon = MediaService.iconCache.get(rs.favicon);
				if (icon == null) {
					icon = ImageHelper.decodeBitmapFromStringUri(context.getApplicationContext(), rs.favicon, 158);
					if (icon != null)
						MediaService.iconCache.put(rs.favicon, icon);
				}
				if (icon != null)
					b.setIconBitmap(icon);
			}

			items.add(new MediaBrowserCompat.MediaItem(b.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
		}
		return items;
	}

	public static void playRadioByUuid(Context context, String uuid, String caller) {
		AppDatabase.databaseReadExecutor.execute(() -> {
			RadioStation rs = AppDatabase.getDatabase(context.getApplicationContext()).radioStationDao()
					.findByUuid(uuid);
			if (rs != null) {
				String streamUrl = (rs.url_resolved != null && !rs.url_resolved.isEmpty()) ? rs.url_resolved : rs.url;
				if (streamUrl != null) {
					new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
						play(context, rs, streamUrl, caller);
					});
				}
			}
		});
	}

	public static String buildShortInfoString(RadioStation s) {
		String codec = !Objects.equals(s.codec, "") ? (!"unknown".equalsIgnoreCase(s.codec) ? s.codec : "" ) : "";
		String bitrate = s.bitrate != 0 ? String.valueOf(s.bitrate) : "";
		String hls = s.hls == 1 ? "HLS" : "";
		/*
		return Stream.of(hls, bitrate, codec)
				.filter(v -> v != null && !v.isEmpty())
				.collect(Collectors.joining(" - "));
		 */
		if (hls.isEmpty()) {
			return Stream.of(codec, bitrate)
					.filter(v -> v != null && !v.isEmpty())
					.collect(Collectors.joining(" - "));

		} else {
			return hls;
		}
	}

	public static int getFlagResource(Context appContext, RadioStation s, boolean languageFirst) {
		int flag_resource = 0;
		if (languageFirst) {
			flag_resource = FlagHelper.getFlagResId(appContext, s.language, "language");
		}

		if (flag_resource == 0) {
			flag_resource = FlagHelper.getFlagResId(appContext, s.country, "country");
		}

		if (flag_resource == 0) {
			flag_resource = FlagHelper.getFlagResId(appContext, s.countrycode, "country");
		}
		if (flag_resource == 0) {
			flag_resource = FlagHelper.getFlagResId(appContext, s.language, "language");
		}
		return flag_resource;
	}
	public static int getFlagResource(Context appContext, RadioStation s) {
		return getFlagResource(appContext, s, false);
	}

}
