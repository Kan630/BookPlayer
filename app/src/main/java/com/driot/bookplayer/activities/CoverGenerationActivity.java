package com.driot.bookplayer.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Locale;

public class CoverGenerationActivity extends LoggingActivity {

    public static final String EXTRA_FOLDER_ID = "folder_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_INITIALS = "initials";
    public static final String EXTRA_DEFAULT_COLOR = "default_color"; // int
    public static final String EXTRA_SIZE_PX = "size_px";
    public static final String EXTRA_ROUNDED = "rounded";

    public static final String RESULT_SAVED_PATH = "result_saved_path";
    public static final String RESULT_INITIALS = "result_initials";
    public static final String RESULT_COLOR = "result_color";
    public static final String RESULT_ROUNDED = "result_rounded";

    private ImageView imgPreview;
    private EditText edtInitials, edtR, edtG, edtB;
    private SeekBar seekR, seekG, seekB;
    private Switch switchRounded;

    private SeekBar seekTextSize;
    private TextView tvTextSizeVal;

    private int sizePx;
    private boolean rounded;
    private int textSizeVal;
    private long folderId;

    private boolean updatingFromSeek = false;
    private boolean updatingFromText = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cover_generation);

        InsetHelper.apply(this);

        imgPreview = findViewById(R.id.imgPreview);
        edtInitials = findViewById(R.id.edtInitials);
        edtR = findViewById(R.id.edtR);
        edtG = findViewById(R.id.edtG);
        edtB = findViewById(R.id.edtB);
        seekR = findViewById(R.id.seekR);
        seekG = findViewById(R.id.seekG);
        seekB = findViewById(R.id.seekB);
        switchRounded = findViewById(R.id.switchRounded);

        seekTextSize = findViewById(R.id.seekTextSize);
        tvTextSizeVal = findViewById(R.id.tvTextSizeVal);

        Button btnCancel = findViewById(R.id.btnCancel);
        Button btnSave = findViewById(R.id.btnSave);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        int defaultColor = getIntent().getIntExtra(EXTRA_DEFAULT_COLOR, ImageHelper.getColorFromTitle(title));
        sizePx = getIntent().getIntExtra(EXTRA_SIZE_PX, 512);
        rounded = getIntent().getBooleanExtra(EXTRA_ROUNDED, true);
        folderId = getIntent().getLongExtra(EXTRA_FOLDER_ID, -1L);
        String passedInitials = getIntent().getStringExtra(EXTRA_INITIALS);

        // Load saved prefs for sliders
        textSizeVal = Pref.getBookCoverTextSize(this, folderId);

        // TextSize slider: min 8 max 30. SeekBar max="22" (0..22) => +8 => 8..30
        seekTextSize.setMax(22);
        seekTextSize.setProgress(textSizeVal - 8);
        tvTextSizeVal.setText(String.valueOf(textSizeVal));

        // Listeners
        seekTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textSizeVal = progress + 8;
                tvTextSizeVal.setText(String.valueOf(textSizeVal));
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Use passed initials if any, else derive from title
        String initInitials = (passedInitials != null && !passedInitials.trim().isEmpty())
                ? passedInitials.trim()
                : getDefaultInitials(title);
        edtInitials.setText(initInitials);
        edtInitials.setText(initInitials);
        updateLineLimit();

        switchRounded.setChecked(rounded);

        int r = Color.red(defaultColor), g = Color.green(defaultColor), b = Color.blue(defaultColor);
        setupNumberBox(edtR);
        setupNumberBox(edtG);
        setupNumberBox(edtB);
        edtR.setText(String.valueOf(r));
        seekR.setMax(255);
        seekR.setProgress(r);
        edtG.setText(String.valueOf(g));
        seekG.setMax(255);
        seekG.setProgress(g);
        edtB.setText(String.valueOf(b));
        seekB.setMax(255);
        seekB.setProgress(b);

        edtInitials.addTextChangedListener(watcher(this::updatePreview));
        switchRounded.setOnCheckedChangeListener((v, isChecked) -> {
            rounded = isChecked;
            updatePreview();
        });

        hookSeekToBox(seekR, edtR);
        hookSeekToBox(seekG, edtG);
        hookSeekToBox(seekB, edtB);
        hookBoxToSeek(edtR, seekR);
        hookBoxToSeek(edtG, seekG);
        hookBoxToSeek(edtB, seekB);

        updatePreview();

        btnCancel.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            // Persist user prefs immediately
            Pref.setBookCoverInitials(this, folderId, getInitials());
            Pref.setBookCoverColor(this, folderId, getRgb());
            Pref.setBookCoverColor(this, folderId, getRgb());
            Pref.setBookCoverRounded(this, folderId, rounded);
            Pref.setBookCoverTextSize(this, folderId, textSizeVal);

            // Do the heavy work off the main thread
            AppDatabase.databaseWriteExecutor.execute(() -> {
                Bitmap bmp = null;
                String savedPath = null;
                try {
                    bmp = render(); // createInitialsBitmapCustom(...)
                    // VERSIONED save (keeps latest, prunes older versions)
                    savedPath = ImageHelper.saveGeneratedInitialsCoverVersioned(
                            this, folderId, getInitials(), getRgb(), rounded, textSizeVal, bmp);
                } catch (Exception e) {
                    myLogEE(e, "CoverGenerationActivity: saveGeneratedInitialsCoverVersioned failed");
                } finally {
                    if (bmp != null && !bmp.isRecycled())
                        bmp.recycle();
                }

                if (savedPath == null || savedPath.isEmpty()) {
                    runOnUiThread(() -> {
                        setResult(RESULT_CANCELED);
                        finish();
                    });
                    return;
                }

                // Return the versioned absolute path to caller (ModifyFolderActivity updates DB
                // & UI)
                Intent out = new Intent()
                        .putExtra(RESULT_SAVED_PATH, savedPath)
                        .putExtra(RESULT_INITIALS, getInitials())
                        .putExtra(RESULT_COLOR, getRgb())
                        .putExtra(RESULT_ROUNDED, rounded);

                runOnUiThread(() -> {
                    setResult(RESULT_OK, out);
                    finish();
                });
            });
        });

    }

    private TextWatcher watcher(Runnable r) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                r.run();
            }
        };
    }

    private void setupNumberBox(EditText e) {
        e.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        e.setSingleLine(true);
    }

    private void hookSeekToBox(SeekBar seek, EditText box) {
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser || updatingFromText)
                    return;
                updatingFromSeek = true;
                box.setText(String.valueOf(p));
                updatingFromSeek = false;
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
    }

    private void hookBoxToSeek(EditText box, SeekBar seek) {
        box.addTextChangedListener(watcher(() -> {
            if (updatingFromSeek)
                return;
            int v = clamp255(box.getText().toString());
            updatingFromText = true;
            seek.setProgress(v);
            updatingFromText = false;
            updatePreview();
        }));
    }

    private int clamp255(String s) {
        if (s == null || s.isEmpty())
            return 0;
        try {
            int v = Integer.parseInt(s);
            if (v < 0)
                v = 0;
            if (v > 255)
                v = 255;
            return v;
        } catch (Exception e) {
            return 0;
        }
    }

    private String getInitials() {
        String s = edtInitials.getText().toString().trim().toUpperCase(Locale.US);
        return s; // No length limit
    }

    private String getDefaultInitials(String title) {
        if (title == null)
            return "AB";
        String t = title.trim();
        if (t.isEmpty())
            return "AB";
        // take first letters of up to 2 words, fallback to first chars
        String[] parts = t.split("\\s+");
        String a = String.valueOf(parts[0].charAt(0));
        String b = parts.length > 1 ? String.valueOf(parts[1].charAt(0)) : "";
        String res = (a + b).toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
        if (res.isEmpty())
            res = t.substring(0, Math.min(2, t.length())).toUpperCase(Locale.US);
        return res;
    }

    private int getRgb() {
        int r = clamp255(edtR.getText().toString());
        int g = clamp255(edtG.getText().toString());
        int b = clamp255(edtB.getText().toString());
        return Color.rgb(r, g, b);
    }

    private Bitmap render() {
        return ImageHelper.createInitialsBitmapCustom(getInitials(), getRgb(), sizePx, rounded,
                textSizeVal);
    }

    private void updatePreview() {
        imgPreview.setImageBitmap(render());
    }

    private void updateLineLimit() {
        edtInitials.setFilters(new InputFilter[] {
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end,
                            android.text.Spanned dest, int dstart, int dend) {
                        // Calculate resulting text
                        String result = dest.subSequence(0, dstart).toString()
                                + source.subSequence(start, end).toString()
                                + dest.subSequence(dend, dest.length()).toString();

                        // Count lines (newlines + 1)
                        int lines = 1;
                        for (int i = 0; i < result.length(); i++) {
                            if (result.charAt(i) == '\n')
                                lines++;
                        }

                        if (lines > 4) { // Hardcoded limit
                            return ""; // Reject change
                        }
                        return null; // Accept
                    }
                }
        });
    }
}
