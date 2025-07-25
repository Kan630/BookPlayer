package com.driot.bookplayer.utils;

//package com.driot.bookplayer.views;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.ClipData;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.driot.bookplayer.R;

public class EditTextWithButtons extends LinearLayout {

    private EditText editText;
    private Button btnPaste;
    private Button btnClear;

    public EditTextWithButtons(Context context) {
        super(context);
        init(context);
    }

    public EditTextWithButtons(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EditTextWithButtons(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_edittext_with_buttons, this, true);
        editText = findViewById(R.id.editText);
        btnPaste = findViewById(R.id.btnPaste);
        btnClear = findViewById(R.id.btnClear);

        btnPaste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence pasteData = clip.getItemAt(0).coerceToText(context);
                    if (!TextUtils.isEmpty(pasteData)) {
                        editText.setText(pasteData);
                        editText.setSelection(pasteData.length());
                    }
                }
            } else {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        });

        btnClear.setOnClickListener(v -> {
            editText.setText("");
        });
    }

    public String getText() {
        return editText.getText().toString();
    }

    public void setText(String text) {
        editText.setText(text);
    }

    public EditText getEditText() {
        return editText;
    }
}
