package com.driot.bookplayer.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.radio.RadioHelper;
import com.driot.bookplayer.utils.Tonio;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * What must (not) exist in the pure build. Pure has no network content sources (radio, podcasts,
 * LibriVox, web cover search) - the whole point of shipping it separately - so a leak of any of
 * them into this APK is a release blocker. All tests skip themselves on full/legacy builds.
 */
@RunWith(AndroidJUnit4.class)
public class PureFlavorTest {

    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        assumeTrue("pure-flavor test", Tonio.isPure(ctx));
    }

    @Test
    public void applicationIdIsThePureOne() {
        assertTrue(ctx.getPackageName(), ctx.getPackageName().startsWith("com.driot.bookplayerpure"));
    }

    @Test
    public void appNameIsThePureOne() {
        CharSequence label = ctx.getApplicationInfo().loadLabel(ctx.getPackageManager());
        assertTrue(label.toString(), label.toString().contains("Pure"));
    }

    @Test
    public void fullOnlyEntityClassesAreNotInThePureBuild() {
        for (String name : new String[] {
                "com.driot.bookplayer.db.Podcast", "com.driot.bookplayer.db.PodcastDao",
                "com.driot.bookplayer.db.Episode", "com.driot.bookplayer.db.EpisodeDao",
                "com.driot.bookplayer.db.RadioStation", "com.driot.bookplayer.db.RadioStationDao",
                "com.driot.bookplayer.db.PendingEpisodeHistory" }) {
            try {
                Class.forName(name);
                fail(name + " leaked into the pure build");
            } catch (ClassNotFoundException expected) {
                // good
            }
        }
    }

    @Test
    public void radioStubsAreInertAndSafe() {
        assertFalse(RadioHelper.hasFavorites(ctx));
        assertTrue(RadioHelper.getFavoriteRadios(ctx).isEmpty());
        assertNull(RadioHelper.getNavToRadioActivityPendingIntent(ctx, 1L));
        assertFalse(RadioHelper.playStreamIfKnownRadio(ctx, "https://example.com/stream"));
        assertFalse(RadioHelper.backupDataHasRadios(new BackupManager.BackupData()));
        // must not throw / start anything
        RadioHelper.handleDeepLink(ctx, Uri.parse("https://radio.driot.com/station/abc"));
        RadioHelper.openRadioStationActivity(ctx, 1L);
        RadioHelper.openRadioStationActivityFromUuid(ctx, "uuid");
        RadioHelper.playRadioByUuid(ctx, "uuid", "test");
        RadioHelper.handleRadioImages(ctx, System.currentTimeMillis());
    }

    @Test
    public void podcastStubsAreInertAndSafe() {
        assertFalse(PodcastHelper.playStreamIfKnownPodcast(ctx, "https://example.com/feed.mp3"));
        assertNull(PodcastHelper.getPodcastOriginalCoverPath(ctx, 1L));
        assertNull(PodcastHelper.getPodcastOriginalCoverUrl(ctx, 1L));
        assertNull(PodcastHelper.getEpisodeCoverForZikFile(ctx, 1L));
        assertFalse(PodcastHelper.backupDataHasPodcasts(new BackupManager.BackupData()));
        assertFalse(PodcastHelper.backupDataHasEpisodeHistory(new BackupManager.BackupData()));
        PodcastHelper.cancelAutoDownload(ctx, 1L);
        PodcastHelper.doAutoDownloadAndDelete(ctx);
        PodcastHelper.handlePodcastImages(ctx, System.currentTimeMillis());
    }

    @Test
    public void noRadioOrPodcastComponentsInTheManifest() throws Exception {
        PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
                PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                        | PackageManager.GET_PROVIDERS);
        StringBuilder bad = new StringBuilder();
        if (pi.activities != null)
            for (ActivityInfo a : pi.activities)
                if (isNetworkContentComponent(a.name))
                    bad.append("activity ").append(a.name).append('\n');
        if (pi.services != null)
            for (ServiceInfo s : pi.services)
                if (isNetworkContentComponent(s.name))
                    bad.append("service ").append(s.name).append('\n');
        assertEquals("radio/podcast components in the pure manifest:\n" + bad, 0, bad.length());
    }

    private static boolean isNetworkContentComponent(String name) {
        String n = name.toLowerCase();
        return n.contains(".radio.") || n.contains(".podcasts.") || n.contains(".librivox.");
    }

    @Test
    public void shareLinksAreNotHandledByThePureApp() {
        // Full's manifest claims these (see src/full/AndroidManifest.xml). If pure also claimed
        // them it would hijack links meant for full, and pure can't play radios anyway.
        for (String link : new String[] {
                "https://bookplayer.driot.com/share/radio/abc",
                "bookplayerfull://radio/abc",
                "bookplayer://radio/abc",
                "https://bookplayer.driot.com/share/podcast?feed=1",
                "bookplayerfull://podcast?feed=1",
                "bookplayer://podcast?feed=1" }) {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(ctx.getPackageName());
            assertNull("pure must not resolve " + link,
                    ctx.getPackageManager().resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY));
        }
    }

    @Test
    public void mainActivityIsLauncherEntry() {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        assertNotNull("no launcher activity", launch);
    }

    @Test
    public void noSensitivePermissionsSneakIntoTheManifest() throws Exception {
        PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
                PackageManager.GET_PERMISSIONS);
        // INTERNET/location are legitimately declared (Firebase, Wi-Fi share) - only guard against
        // permissions this app never needs. Extend the deny-list if another must never ship.
        if (pi.requestedPermissions != null) {
            for (String p : pi.requestedPermissions) {
                assertFalse(p, p.equals("android.permission.READ_SMS") || p.equals("android.permission.SEND_SMS")
                        || p.equals("android.permission.READ_CONTACTS")
                        || p.equals("android.permission.READ_CALL_LOG")
                        || p.equals("android.permission.CAMERA"));
            }
        }
    }
}
