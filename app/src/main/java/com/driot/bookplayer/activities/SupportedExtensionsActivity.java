package com.driot.bookplayer.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SupportedExtensionsActivity extends LoggingActivity {

    private static final String EXTRA_MSG = "extra_msg";

    public static Intent newIntent(Context ctx, String message) {
        Intent i = new Intent(ctx, SupportedExtensionsActivity.class);
        i.putExtra(EXTRA_MSG, message);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supported_extensions);
        InsetHelper.apply(this);

        TextView tvFileMime = findViewById(R.id.tvFileMime);
        Button btnOk = findViewById(R.id.btnOk);

        String msg = getIntent().getStringExtra(EXTRA_MSG);
        tvFileMime.setText(msg != null ? msg : "....");

        List<Section> data = buildSections();
        RecyclerView rv = findViewById(R.id.rvSections);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new SectionsAdapter(buildSections()));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        btnOk.setOnClickListener(v -> finish());
    }

    private List<Section> buildSections() {
        List<Section> sections = new ArrayList<>();

        sections.add(new Section(
                getString(R.string.Audio),
                toSortedDotList(Var.SUPPORTED_AUDIO_EXTENSIONS)
        ));
        sections.add(new Section(
                getString(R.string.Video),
                toSortedDotList(Var.SUPPORTED_VIDEO_EXTENSIONS)
        ));
        sections.add(new Section(
                getString(R.string.Text),
                toSortedDotList(Var.SUPPORTED_EBOOK_EXTENSIONS)
        ));
        sections.add(new Section(
                getString(R.string.Bundle),
                toSortedDotList(Var.SUPPORTED_COMPRESSED_FILE_EXTENSIONS)
        ));
        return sections;
    }

// SupportedExtensionsActivity.java (add inside the class, below buildSections())

    private static class SectionsAdapter extends RecyclerView.Adapter<SectionsAdapter.SectionVH> {
        private final List<Section> sections;

        SectionsAdapter(List<Section> sections) {
            this.sections = sections != null ? sections : new ArrayList<>();
        }

        @NonNull @Override
        public SectionVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_section_with_grid, parent, false);
            return new SectionVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SectionVH holder, int position) {
            holder.bind(sections.get(position));
        }

        @Override public int getItemCount() { return sections.size(); }

        static class SectionVH extends RecyclerView.ViewHolder {
            final TextView tvHeader;
            final RecyclerView rvGrid;

            SectionVH(@NonNull View itemView) {
                super(itemView);
                tvHeader = itemView.findViewById(R.id.tvHeader);
                rvGrid = itemView.findViewById(R.id.rvGrid);
            }

            void bind(Section s) {
                tvHeader.setText(s.title);

                // 4 columns grid; adjust to taste (e.g., 3 or 5)
                GridLayoutManager glm = new GridLayoutManager(itemView.getContext(), 4);
                rvGrid.setLayoutManager(glm);
                rvGrid.setAdapter(new ExtensionGridAdapter(s.items));
                //rvGrid.setHasFixedSize(true);
            }
        }
    }
// SupportedExtensionsActivity.java (add inside the class, after SectionsAdapter)

    private static class ExtensionGridAdapter extends RecyclerView.Adapter<ExtensionGridAdapter.VH> {
        private final List<String> items;

        ExtensionGridAdapter(List<String> items) {
            this.items = items != null ? items : new ArrayList<>();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_extension_chip, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(items.get(position));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(@NonNull View itemView) {
                super(itemView);
                tv = itemView.findViewById(R.id.tvExt);
            }
            void bind(String ext) { tv.setText(ext); }
        }
    }


    private static List<String> toSortedDotList(java.util.Set<String> set) {
        List<String> list = new ArrayList<>(set.size());
        for (String s : set) {
            if (s == null) continue;
            s = s.trim();
            if (s.isEmpty()) continue;
            // show as ".mp3"
            list.add("." + s.toLowerCase());
        }
        Collections.sort(list);
        return list;
    }

    // --- Simple section model ---
    private static class Section {
        final String title;
        final List<String> items;
        Section(String title, List<String> items) {
            this.title = title;
            this.items = items;
        }
    }

    // --- RecyclerView with headers + items ---
    private static class SectionedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VT_HEADER = 1;
        private static final int VT_ITEM = 2;

        private static class Row {
            final int type; // header/item
            final String text;
            Row(int type, String text) { this.type = type; this.text = text; }
        }

        private final List<Row> rows = new ArrayList<>();

        SectionedAdapter(List<Section> sections) {
            for (Section s : sections) {
                rows.add(new Row(VT_HEADER, s.title));
                for (String it : s.items) rows.add(new Row(VT_ITEM, it));
            }
        }

        @Override public int getItemViewType(int position) { return rows.get(position).type; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == VT_HEADER) {
                View v = inf.inflate(R.layout.item_section_header, parent, false);
                return new HeaderVH(v);
            } else {
                View v = inf.inflate(R.layout.item_extension, parent, false);
                return new ItemVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row r = rows.get(position);
            if (holder instanceof HeaderVH) ((HeaderVH) holder).bind(r.text);
            else if (holder instanceof ItemVH) ((ItemVH) holder).bind(r.text);
        }

        @Override public int getItemCount() { return rows.size(); }

        static class HeaderVH extends RecyclerView.ViewHolder {
            final TextView tv;
            HeaderVH(@NonNull View itemView) { super(itemView); tv = itemView.findViewById(R.id.tvHeader); }
            void bind(String t) { tv.setText(t); }
        }

        static class ItemVH extends RecyclerView.ViewHolder {
            final TextView tv;
            ItemVH(@NonNull View itemView) { super(itemView); tv = itemView.findViewById(R.id.tvExt); }
            void bind(String t) { tv.setText(t); }
        }
    }
}
