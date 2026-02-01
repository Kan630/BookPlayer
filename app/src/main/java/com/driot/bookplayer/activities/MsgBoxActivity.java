    package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class MsgBoxActivity extends BaseActivity {

    // Types
    public static final int TYPE_INFO = 0;
    public static final int TYPE_ALERT = 1;
    public static final int TYPE_QUESTION = 2;

    @IntDef({TYPE_INFO, TYPE_ALERT, TYPE_QUESTION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MsgType {
    }

    public static final String RESULT_WHICH = "result_which";
    public static final int WHICH_POSITIVE = 1;
    public static final int WHICH_NEGATIVE = 0;
    public static final int WHICH_NEUTRAL = 2;

    // Extras
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_DETAILS = "details";
    public static final String EXTRA_POSITIVE = "positive";
    public static final String EXTRA_NEGATIVE = "negative";
    public static final String EXTRA_CHECKBOX_TEXT = "checkbox_text";
    public static final String EXTRA_ICON_RES = "icon_res";
    public static final String EXTRA_NEUTRAL = "neutral";         // text
    public static final String EXTRA_NEUTRAL_INTENT = "neutral_intent";  // optional Intent to launch

    public static final String RESULT_CHECKED = "result_checked";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_msgbox);

        InsetHelper.apply(this);

        View root = findViewById(R.id.root);
        ImageView icon = findViewById(R.id.icon);
        TextView title = findViewById(R.id.title);
        TextView message = findViewById(R.id.message);
        TextView details = findViewById(R.id.details);
        MaterialCheckBox checkbox = findViewById(R.id.checkbox);
        MaterialButton btnPositive = findViewById(R.id.btnPositive);
        MaterialButton btnNegative = findViewById(R.id.btnNegative);
        MaterialButton btnNeutral = findViewById(R.id.btnNeutral);

        Intent it = getIntent();
        int type = it.getIntExtra(EXTRA_TYPE, TYPE_INFO);

        String t = it.getStringExtra(EXTRA_TITLE);
        String msg = it.getStringExtra(EXTRA_MESSAGE);
        String det = it.getStringExtra(EXTRA_DETAILS);
        String pos = it.getStringExtra(EXTRA_POSITIVE);
        String neg = it.getStringExtra(EXTRA_NEGATIVE);
        String cbText = it.getStringExtra(EXTRA_CHECKBOX_TEXT);
        int iconRes = it.getIntExtra(EXTRA_ICON_RES, 0);
        String neutral = it.getStringExtra(EXTRA_NEUTRAL);
        Intent neutralIntent;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            neutralIntent = it.getParcelableExtra(EXTRA_NEUTRAL_INTENT, Intent.class);
        } else {
            neutralIntent = it.getParcelableExtra(EXTRA_NEUTRAL_INTENT);
        }

        title.setText(t != null ? t : getString(R.string.app_name));
        message.setText(msg != null ? msg : "");

        if (det != null && !det.isEmpty()) {
            details.setText(det);
            details.setVisibility(View.VISIBLE);
        }

        if (cbText != null && !cbText.isEmpty()) {
            checkbox.setText(cbText);
            checkbox.setVisibility(View.VISIBLE);
        }

        // Icône par défaut selon type
        if (iconRes == 0) {
            iconRes = defaultIconFor(type);
        }
        if (iconRes != 0) icon.setImageResource(iconRes);

        // Boutons

        if (type == TYPE_QUESTION) {
            btnNegative.setVisibility(View.VISIBLE);
            btnNegative.setText(neg != null ? neg : getString(android.R.string.cancel));
            btnPositive.setText(pos != null ? pos : getString(android.R.string.ok));
        } else {
            btnNegative.setVisibility(View.GONE);
            btnPositive.setText(pos != null ? pos : getString(android.R.string.ok));
        }
        if (neutral != null && !neutral.isEmpty()) {
            btnNeutral.setVisibility(View.VISIBLE);
            btnNeutral.setText(neutral);
        } else {
            btnNeutral.setVisibility(View.GONE);
        }

        // Clicks
        btnNeutral.setOnClickListener(v -> {
            // If caller provided an Intent (e.g., open app settings), launch it
            if (neutralIntent != null) {
                try {
                    startActivity(neutralIntent);
                } catch (Throwable ignored) {
                }
            }
            setResult(RESULT_OK, resultData(WHICH_NEUTRAL, checkbox));
            finish();
        });

        btnNegative.setOnClickListener(v -> {
            setResult(RESULT_CANCELED, resultData(WHICH_NEGATIVE, checkbox));
            finish();
        });

        btnPositive.setOnClickListener(v -> {
            setResult(RESULT_OK, resultData(WHICH_POSITIVE, checkbox));
            finish();
        });

        // Fermer si on tape dans le scrim (en info/alerte uniquement)
        root.setOnClickListener(v -> {
            if (type != TYPE_QUESTION) {
                setResult(RESULT_CANCELED, withCheck(it, checkbox));
                finish();
            }
        });
        findViewById(R.id.card).setOnClickListener(v -> {/* bloquer propagation */});
        myLog("MsgBox created.   type=" + type);
    }

    private Intent withCheck(Intent src, MaterialCheckBox cb) {
        Intent data = new Intent();
        data.putExtra(RESULT_CHECKED, cb.getVisibility() == View.VISIBLE && cb.isChecked());
        return data;
    }

    @DrawableRes
    private int defaultIconFor(@MsgType int type) {
        if (type == TYPE_ALERT) return R.drawable.ic_warning_24;
        if (type == TYPE_QUESTION) return R.drawable.ic_help_24;
        return R.drawable.ic_info_24;
    }


    private Intent resultData(int which, MaterialCheckBox cb) {
        Intent data = new Intent();
        data.putExtra(RESULT_CHECKED, cb.getVisibility() == View.VISIBLE && cb.isChecked());
        data.putExtra(RESULT_WHICH, which);
        return data;
    }


    // Helpers statiques pratiques

    public static Intent buildInfo(android.content.Context ctx, String title, String message, @Nullable String details) {
        return new Intent(ctx, MsgBoxActivity.class)
                .putExtra(EXTRA_TYPE, TYPE_INFO)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_DETAILS, details);
    }

    public static Intent buildAlert(android.content.Context ctx, String title, String message, @Nullable String details) {
        return new Intent(ctx, MsgBoxActivity.class)
                .putExtra(EXTRA_TYPE, TYPE_ALERT)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_DETAILS, details);
    }

    public static Intent buildQuestion(android.content.Context ctx, String title, String message,
                                       @Nullable String details,
                                       @Nullable String positiveText,
                                       @Nullable String negativeText) {
        return new Intent(ctx, MsgBoxActivity.class)
                .putExtra(EXTRA_TYPE, TYPE_QUESTION)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_DETAILS, details)
                .putExtra(EXTRA_POSITIVE, positiveText)
                .putExtra(EXTRA_NEGATIVE, negativeText);
    }
}
