package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.global.PlayList;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.Tonio.FormatLastAccess;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.FormatPercentForProgressBar;
import static com.driot.bookplayer.utils.Tonio.FormatPercentString;
import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;
import static com.driot.tonylib.KanLogger.myLogInFile;
import static com.driot.tonylib.KanLogger.myToast;
import static com.driot.tonylib.KanLogger.myToastE;


public class ZikFilesAdapter extends RecyclerView.Adapter<ZikFilesAdapter.ZikFilesViewHolder> {

    private Context mCtx;
    private List<ZikFile> zikFileList;

    public ZikFilesAdapter(Context mCtx, List<ZikFile> zikFileList) {
        this.mCtx = mCtx;
        this.zikFileList = zikFileList;
    }

    @Override
    public ZikFilesViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtx).inflate(R.layout.recyclerview_zikfiles, parent, false);
        return new ZikFilesViewHolder(view);
    }

    /********************************************************************************
     ***       SETTING VALUES
     ********************************************************************************
     */
    @Override
    public void onBindViewHolder(ZikFilesViewHolder holder, int position) {
        RedrawViewHolderElements(holder, position);
    }

    public void RedrawViewHolderElements(ZikFilesViewHolder holder, int position) {
        ZikFile t = zikFileList.get(position);

        holder.textViewFileName.setText(t.getDisplayName());

        holder.textViewFilePercent.setText(FormatPercentString(t.getPercentdone()));

        holder.mProgressBar.setProgress(FormatPercentForProgressBar(t.getPercentdone()));

        holder.textViewFileLastAccess.setText(FormatLastAccess(t.getLastaccess(), t.getLastaccessTime(), mCtx.getString(R.string.yesterday)));

        holder.textViewDuration.setText(FormatTime(t.getDuration()));

    }


    @Override
    public int getItemCount() {
        return zikFileList.size();
    }

    class ZikFilesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;


        public ZikFilesViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration = itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        ////////////////////// CLICK
        @Override
        public void onClick(View view) {
            ZikFile zikFile = zikFileList.get(getAdapterPosition());
            myLog("Adapater : onClick() : " + zikFile.getName());

            boolean FileOkForPlay = false;
            // check First that zikFile is proper zikFile and is playable
            if (zikFile.isIszipfile()) {
                FileOkForPlay = true;
                myLog("Adapater : zikFile is zip");
            } else {
                // check file exists
/*
                if (zikFile.isIsSingleFile()) {
                    String fullPath = zikFile.getPath();
                } else {
                    String fullPath = zikFile.getPath() + "/" + zikFile.getName();
                }
*/
                String fullPath = zikFile.getPath() + "/" + zikFile.getName();
                myLog("Adapater : full path zikFile to open PlayActivity : " + fullPath);
                if (fileExists(fullPath)) FileOkForPlay = true;
            }

            if (FileOkForPlay) {
                //PlayList.setZikFile(zikFile); //global var
                PlayList.setNumZikFile(getAdapterPosition()); //global var

                //pass an object, check parcelable //on s'en sert plus.... tout semble passer par les global var ci dessus
                Intent intent = new Intent(mCtx, PlayActivity.class);
                intent.putExtra("ZikFile", zikFile);
                mCtx.startActivity(intent);
            } else {
                myLogE("Adapater : opening PlayActivity -- ERROR OPENING TRACK - FILE NOT FOUND !");
                Toast.makeText(mCtx, mCtx.getString(R.string.PlayActivity_ErrorOpeningTrack_FileNotFound), Toast.LENGTH_SHORT).show();
            }
        }


        @Override
        public boolean onLongClick(View view) {
            ZikFile zikFile = zikFileList.get(getAdapterPosition());

            Intent intent = new Intent(mCtx, ZikFileModifyActivity.class);
            intent.putExtra("ZikFile", zikFile);

            mCtx.startActivity(intent);
            return false;
        }
    }

    private void bDeleteLongClick(int idFolder, String zikFileName) {
        new AlertDialog.Builder(mCtx)
                .setTitle(mCtx.getString(R.string.ModifyFolder_AskDeleteProgressFromZikFile_Title))
                .setMessage(mCtx.getString(R.string.ModifyFolder_AskDeleteProgressFromZikFile_Text))
                .setCancelable(false)
                .setPositiveButton(mCtx.getString(R.string.yes), (dialog, which) -> deleteProgressFromThisZikFile(idFolder, zikFileName))
                .setNegativeButton(mCtx.getString(R.string.cancel), (dialogInterface, i) -> {})
                .show();
    }

    private void deleteProgressFromThisZikFile(int idFolder, String zikFileName) {
        Observable.fromCallable(() -> {
            DatabaseClient
                    .getInstance(mCtx.getApplicationContext())
                    .getAppDatabase()
                    .ZikFileDao()
                    .resetProgressionFromThisZikFile(idFolder, zikFileName);
            return true;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result) {
                        myToast(mCtx.getString(R.string.Progression_reset_done));
                        myLogInFile(mCtx.getString(R.string.Progression_reset_done) + " beggining on " + zikFileName);
                        Sql.calculateFolderProgress(mCtx, idFolder);
                        reLoad(idFolder);
                    }
                }, throwable -> {
                    myToastE("Adapater : error deleting progress");
                    myLogE("Adapater : error deleteProgressFromThisZikFile :" + throwable.getMessage());
                    throwable.printStackTrace();
                });

    }

    private void reLoad(int idFolder) {
        ((ZikFileActivity)mCtx).getZikFiles(idFolder);
    }

}
