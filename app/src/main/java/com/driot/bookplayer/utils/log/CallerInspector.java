package com.driot.bookplayer.utils.log;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;

import androidx.core.app.ActivityCompat;

import java.util.Set;

public final class CallerInspector {
    private CallerInspector() {}

    public static String inferCaller(Activity a, String extraKeyCaller) {
        final Intent intent = a.getIntent();
        if (intent == null) return "unknown(intent=null)";

        // 1) Priorité: extra explicite posé par l’appelant (recommandé)
        final String extraCaller = intent.getStringExtra(extraKeyCaller);
        if (extraCaller != null && !extraCaller.isEmpty()) {
            return "extra:" + extraCaller;
        }

        // 2) startActivityForResult (héritage)
        final ComponentName ca = a.getCallingActivity();
        if (ca != null) {
            return "callingActivity:" + ca.flattenToShortString();
        }

        // 3) Referrer (API 22+, via ActivityCompat pour compat)
        final Uri ref = ActivityCompat.getReferrer(a);
        if (ref != null) {
            // android-app://package.name => on récupère le host quand possible
            if ("android-app".equals(ref.getScheme()) && ref.getHost() != null) {
                return "referrer:" + ref.getHost();
            } else {
                return "referrer:" + ref.toString();
            }
        }

        // 4) Heuristiques sur l’Intent
        final StringBuilder sb = new StringBuilder();
        if (intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            sb.append("launcher");
        } else if (intent.getAction() != null) {
            sb.append("action:").append(intent.getAction());
        } else {
            sb.append("unknown");
        }

        // Ajoute quelques indices utiles (catégories, flags)
        final Set<String> cats = intent.getCategories();
        if (cats != null && !cats.isEmpty()) {
            sb.append(" cats:").append(cats);
        }
        sb.append(" flags:0x").append(Integer.toHexString(intent.getFlags()));

        return sb.toString();
    }
}
