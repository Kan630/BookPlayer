package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromUri;
import static com.driot.bookplayer.utils.Tonio.getMimeType;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.KanLogger;

import java.util.Objects;

public class LoadOptionsActivity extends Activity {

    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TYPE = "type";  // File or Folder
    //public static final String EXTRA_TITLE = "title";

    private Uri uri;
    private String type, title;

    private CheckBox cbSplit, cbCopy, cbDelete;
    private LinearLayout llSplit, llCopy, llDelete;

    private boolean internalCheckBoxStateCalculationInProgress;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_options);

        uri = getIntent().getParcelableExtra(EXTRA_URI);
        type = Objects.toString(getIntent().getStringExtra(EXTRA_TYPE),"");

        if (!(type.equals("File")) && !(type.equals("Folder"))) {
            myToastE("Unsupported type : [" + type + "]");
            finish();
        }
        if (Objects.isNull(uri)) {
            myToastE("Unrecognized picked object : [uri is null]");
            finish();
        }
        //title = getIntent().getStringExtra(EXTRA_TITLE);

        TextView tvFileName = findViewById(R.id.tvFileName);
        TextView tvMimeExtension = findViewById(R.id.tvMimeExtension);
        Button btnConfirm = findViewById(R.id.btnConfirm);
        Button btnCancel = findViewById(R.id.btnCancel);


        String uriPath = uri.getPath();
        myLog("picked data : " + uriPath);

        if (type.equals("File")) {
            String mimeType = Objects.toString(getMimeType(this, uri),"");
            String fileName = getFileNameFromUri(this, uri);
            String fileExtension = getExtension(fileName);
            String infoMimeExtension = "[" + mimeType + "] - [." + fileExtension + "]";
            tvMimeExtension.setText(infoMimeExtension);
            if (Objects.toString(fileExtension,"").isEmpty()) {
                myToastE("Error : file extension not found");
                finish();
            }

            if (fileExtension.equalsIgnoreCase("zip")) {
                type = "ZIP";
            } else if (fileExtension.equalsIgnoreCase("m4b")) {
                type = "M4B";
            }

            if (mimeType.startsWith(ONLY_MIME_AUDIO) || SUPPORTED_AUDIO_EXTENSIONS.contains(fileExtension)) {
                myLog("ok mime - " + infoMimeExtension);
            } else {
                myLogE("mime ko - " + infoMimeExtension);
            }
        } else if (type.equals("Folder")) {
            findViewById(R.id.llMimeExtension).setVisibility(View.GONE);
        }

        title = "[" + type + "] - " + formatNameForDisplay(getFileNameFromPath(uriPath));
        myLog("title : " + title);
        tvFileName.setText(title);




/// /// OPTIONS
        cbSplit = findViewById(R.id.cbSplitM4B);
        cbCopy = findViewById(R.id.cbCopyInternal);
        cbDelete = findViewById(R.id.cbDeleteSource);
        llSplit = findViewById(R.id.ll_split_m4b);
        llCopy = findViewById(R.id.ll_copy_internal);
        llDelete = findViewById(R.id.ll_delete_source);

        llSplit.setOnClickListener(v -> cbSplit.toggle());
        llCopy.setOnClickListener(v -> cbCopy.toggle());
        llDelete.setOnClickListener(v -> cbDelete.toggle());

        cbSplit.setChecked(Option.getSplitM4b(this));
        cbCopy.setChecked(Option.getCopyFile(this));
        cbDelete.setChecked(Option.getDeleteSourceFile(this));
        calculateCheckboxState();

        cbSplit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!internalCheckBoxStateCalculationInProgress) calculateCheckboxState();
        });
        cbCopy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!internalCheckBoxStateCalculationInProgress) calculateCheckboxState();
        });
        cbDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!internalCheckBoxStateCalculationInProgress) {
                if (isChecked) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.option_alert_delete_picked_source_file_title))
                            .setMessage(getString(R.string.option_alert_delete_picked_source_file_message))
                            .setCancelable(false)
                            .setPositiveButton("ok", (dialog, which) -> calculateCheckboxState())
                            .setNegativeButton("cancel", (dialog, which) -> cbDelete.setChecked(false))
                            .show();
                } else {
                    Option.setDeleteSourceFile(this, false);
                }
            }
        });

/// /// OPTIONS



        btnCancel.setOnClickListener(v -> finish());

        btnConfirm.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra("uri", uri);
            data.putExtra("type", type);
            data.putExtra("title", title);
            data.putExtra("split", cbSplit.isChecked());
            data.putExtra("copy", cbCopy.isChecked());
            data.putExtra("delete", cbDelete.isChecked());
            setResult(RESULT_OK, data);
            finish();
        });
    }

    private void calculateCheckboxState() {
        internalCheckBoxStateCalculationInProgress = true;

        if ("M4B".equals(type)) {
            llSplit.setVisibility(View.VISIBLE);
            if (cbSplit.isChecked()) {
                cbCopy.setEnabled(false);
                llCopy.setEnabled(false);
                cbCopy.setChecked(true);
            } else {
                cbCopy.setEnabled(true);
                llCopy.setEnabled(true);
            }
        } else {
            llSplit.setVisibility(View.GONE);
        }

        if ("ZIP".equals(type)) {
            cbCopy.setEnabled(false);
            cbCopy.setChecked(true);
        }

        if (cbCopy.isChecked()) {
            cbDelete.setEnabled(true);
            llDelete.setEnabled(true);
        } else {
            cbDelete.setChecked(false);
            cbDelete.setEnabled(false);
            llDelete.setEnabled(false);
        }
        internalCheckBoxStateCalculationInProgress = false;
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
