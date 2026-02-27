package com.driot.bookplayer.settings.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;

import com.google.android.material.checkbox.MaterialCheckBox;

import android.widget.LinearLayout;

import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled;
import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled_all;

public class ImportSettingsFragment extends LoggingFragment {

    private LinearLayout llContainerSdCard;
    private MaterialCheckBox chkUseSdCard;

    private LinearLayout llCopyFile;
    private MaterialCheckBox chkCopyFile;

    private LinearLayout llCreateCover;
    private MaterialCheckBox chkCreateCover;

    private LinearLayout llMetadataTitles;
    private MaterialCheckBox chkMetadataTitles;

    private LinearLayout llSplitM4b;
    private MaterialCheckBox chkSplitM4b;

    private LinearLayout llDeleteSourceFile;
    private MaterialCheckBox chkDeleteSourceFile;

    private LinearLayout llOpenWith;
    private MaterialCheckBox chkOpenWith;

    private LinearLayout llOpenWithAll;
    private MaterialCheckBox chkOpenWithAll;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings_import, container, false);

        // Local title visibility (hidden when embedded inline)
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null)
            showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        // ====== SD Card block ======
        llContainerSdCard = root.findViewById(R.id.ll_container_sd_card);
        chkUseSdCard = root.findViewById(R.id.chk_use_sd_card);
        LinearLayout llUseSdCard = root.findViewById(R.id.ll_use_sd_card);

        boolean sdAvailable = StorageHelper.isExternalSDCardAvailable(requireContext());
        llContainerSdCard.setVisibility(sdAvailable ? View.VISIBLE : View.GONE);
        if (sdAvailable) {
            chkUseSdCard.setChecked(Option.getUseSdCard());
            llUseSdCard.setOnClickListener(v -> chkUseSdCard.toggle());
            chkUseSdCard.setOnCheckedChangeListener((button, checked) -> Option.setUseSdCard(checked));
        }

        // ====== Copy file ======
        chkCopyFile = root.findViewById(R.id.chk_copy_file);
        llCopyFile = root.findViewById(R.id.ll_copy_file);
        chkCopyFile.setChecked(Option.getCopyFile());
        llCopyFile.setOnClickListener(v -> chkCopyFile.toggle());
        chkCopyFile.setOnCheckedChangeListener((button, checked) -> Option.setCopyFile(checked));

        // ====== Create cover ======
        chkCreateCover = root.findViewById(R.id.chk_create_cover);
        llCreateCover = root.findViewById(R.id.ll_create_cover);
        chkCreateCover.setChecked(Option.getCreateCover());
        llCreateCover.setOnClickListener(v -> chkCreateCover.toggle());
        chkCreateCover.setOnCheckedChangeListener((button, checked) -> Option.setCreateCover(checked));

        // ====== Metadata titles ======
        chkMetadataTitles = root.findViewById(R.id.chk_metadata_titles);
        llMetadataTitles = root.findViewById(R.id.ll_metadata_titles);
        chkMetadataTitles.setChecked(Option.getUseMetadataTitles());
        llMetadataTitles.setOnClickListener(v -> chkMetadataTitles.toggle());
        chkMetadataTitles.setOnCheckedChangeListener((button, checked) -> Option.setUseMetadataTitles(checked));

        // ====== Split m4b ======
        chkSplitM4b = root.findViewById(R.id.chk_split_m4b);
        llSplitM4b = root.findViewById(R.id.ll_split_m4b);
        chkSplitM4b.setChecked(Option.getSplitM4b());
        llSplitM4b.setOnClickListener(v -> chkSplitM4b.toggle());
        chkSplitM4b.setOnCheckedChangeListener((button, checked) -> Option.setSplitM4b(checked));

        // ====== Delete source file (with confirm) ======
        chkDeleteSourceFile = root.findViewById(R.id.chk_delete_source_file);
        llDeleteSourceFile = root.findViewById(R.id.ll_delete_source_file);
        chkDeleteSourceFile.setChecked(Option.getDeleteSourceFile());
        llDeleteSourceFile.setOnClickListener(v -> chkDeleteSourceFile.toggle());
        chkDeleteSourceFile.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.option_alert_delete_source_file_title))
                        .setMessage(getString(R.string.option_alert_delete_source_file_message))
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            myLogI("alertDialog : user clicks ok");
                            Option.setDeleteSourceFile(true);
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            // revert UI state
                            myLogI("alertDialog : user clicks cancel");
                            chkDeleteSourceFile.setChecked(Option.getDeleteSourceFile());
                        })
                        .show();
            } else {
                Option.setDeleteSourceFile(false);
            }
        });

        // ====== Open With / Open With (All) ======
        chkOpenWith = root.findViewById(R.id.chk_open_with);
        llOpenWith = root.findViewById(R.id.ll_open_with);
        chkOpenWith.setChecked(Option.getOpenWith());
        llOpenWith.setOnClickListener(v -> chkOpenWith.toggle());
        chkOpenWith.setOnCheckedChangeListener((button, checked) -> {
            Option.setOpenWith(checked);
            setOpenWithProxyEnabled(requireContext(), checked);
        });

        chkOpenWithAll = root.findViewById(R.id.chk_open_with_all);
        llOpenWithAll = root.findViewById(R.id.ll_open_with_all);
        chkOpenWithAll.setChecked(Option.getOpenWith_all());
        llOpenWithAll.setOnClickListener(v -> chkOpenWithAll.toggle());
        chkOpenWithAll.setOnCheckedChangeListener((button, checked) -> {
            Option.setOpenWith_all(checked);
            setOpenWithProxyEnabled_all(requireContext(), checked);
        });

        // Optional: highlight header text red if you still want that behavior
        // TextView txtCopyFileHead = root.findViewById(R.id.txtCopyFileHead);
        // if (getActivity() != null &&
        // getActivity().getIntent().getBooleanExtra("CopyFileSetRed", false)) {
        // txtCopyFileHead.setTextColor(ContextCompat.getColor(requireContext(),
        // R.color.red_500));
        // }

        return root;
    }
}
