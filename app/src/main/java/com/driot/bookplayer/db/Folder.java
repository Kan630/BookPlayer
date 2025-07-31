package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.helpers.StorageHelper;

@Entity
public class Folder implements Parcelable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "path")
    private String path;

    @ColumnInfo(name = "uri")
    private String uri;

    @ColumnInfo(name = "hash")
    private String hash;

    @ColumnInfo(name = "position")
    private int position;

    @ColumnInfo(name = "duration")
    private double duration;

    @ColumnInfo(name = "percentdone")
    private Double percentdone;

    @ColumnInfo(name = "iszipfile")
    private boolean iszipfile;

    @ColumnInfo(name = "finished")
    private boolean finished;

    @ColumnInfo
    private String originalType;

    @ColumnInfo
    private String originalFile;

    @ColumnInfo
    private String originalHash;

    @ColumnInfo
    private String sourceLocation;

    @ColumnInfo
    private long listeningDuration;

    @ColumnInfo
    private long listeningPlayCount;

    public long nbZikFile;

    public long date_added;

    public long date_last_zikfile_added;

    public String image;

    public Long lFirstAccess;

    public long lLastAccess;


    /*
     * Getters and Setters
     * */

    public Folder() {
        // Default constructor required by Room
    }


    @Ignore
    protected Folder(Parcel in) {
        id = in.readInt();
        name = in.readString();
        path = in.readString();
        uri = in.readString();
        hash = in.readString();
        position = in.readInt();
        duration = in.readDouble();
        percentdone = in.readByte() == 0 ? null : in.readDouble();
        iszipfile = in.readByte() != 0;
        finished = in.readByte() != 0;
        originalType = in.readString();
        originalFile = in.readString();
        originalHash = in.readString();
        sourceLocation = in.readString();
        listeningDuration = in.readLong();
        listeningPlayCount = in.readLong();
        nbZikFile = in.readLong();
        date_added = in.readLong();
        date_last_zikfile_added = in.readLong();
        image = in.readString();
        lFirstAccess = in.readByte() == 0 ? null : in.readLong();
        lLastAccess = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeString(path);
        dest.writeString(uri);
        dest.writeString(hash);
        dest.writeInt(position);
        dest.writeDouble(duration);
        if (percentdone == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeDouble(percentdone);
        }
        dest.writeByte((byte) (iszipfile ? 1 : 0));
        dest.writeByte((byte) (finished ? 1 : 0));
        dest.writeString(originalType);
        dest.writeString(originalFile);
        dest.writeString(originalHash);
        dest.writeString(sourceLocation);
        dest.writeLong(listeningDuration);
        dest.writeLong(listeningPlayCount);
        dest.writeLong(nbZikFile);
        dest.writeLong(date_added);
        dest.writeLong(date_last_zikfile_added);
        dest.writeString(image);
        if (lFirstAccess == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeLong(lFirstAccess);
        }
        dest.writeLong(lLastAccess);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Folder> CREATOR = new Creator<Folder>() {
        @Override
        public Folder createFromParcel(Parcel in) {
            return new Folder(in);
        }

        @Override
        public Folder[] newArray(int size) {
            return new Folder[size];
        }
    };

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public Double getPercentdone() {
        if (percentdone==null) {
            return 0.0;
        } else {
            return percentdone;
        }
    }

    public void setPercentdone(Double percentdone) {
        this.percentdone = percentdone;
    }

    public boolean isIszipfile() {
        return iszipfile;
    }

    public void setIszipfile(boolean iszipfile) {
        this.iszipfile = iszipfile;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }


    public int getMemoryLocationIcon(Context context) {
        StorageHelper.MemoryLocationType type = StorageHelper.getMemoryLocationType(context, path);
        switch (type) {
            case INTERNAL_RESERVED:
                return R.drawable.ic_memory_general_smartphone_r;
            case SDCARD_RESERVED:
                return R.drawable.ic_memory_sdcard_r;
            case SDCARD_SHARED:
                return R.drawable.ic_memory_sdcard;
            case PHONE_SHARED:
                return R.drawable.ic_memory_general_smartphone;
            default:
                return R.drawable.ic_memory_notfound;
        }
    }

    public String getMemoryLocationText(Context context) {
        StorageHelper.MemoryLocationType type = StorageHelper.getMemoryLocationType(context, path);
        switch (type) {
            case INTERNAL_RESERVED:
                return context.getString(R.string.audio_location_bookplayer_reserved_storage);
            case SDCARD_RESERVED:
                return context.getString(R.string.audio_location_sdcard_reserved_storage);
            case SDCARD_SHARED:
                return context.getString(R.string.audio_location_sdcard);
            case PHONE_SHARED:
                return context.getString(R.string.audio_location_smartphone_shared_storage);
            default:
                return context.getString(R.string.audio_location_audiobook_not_found);
        }
    }

    public String getOriginalType() {
        return originalType;
    }

    public String getOriginalFile() {
        return originalFile;
    }

    public String getOriginalHash() {
        return originalHash;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }


    public void setOriginalType(String originalType) {
        this.originalType = originalType;
    }

    public void setOriginalFile(String originalFile) {
        this.originalFile = originalFile;
    }

    public void setOriginalHash(String originalHash) {
        this.originalHash = originalHash;
    }

    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }
    public long getListeningDuration() {
        return listeningDuration;
    }

    public long getListeningPlayCount() {
        return listeningPlayCount;
    }
    public void setListeningDuration(long listeningDuration) {
        this.listeningDuration = listeningDuration;
    }

    public void setListeningPlayCount(long listeningPlayCount) {
        this.listeningPlayCount = listeningPlayCount;
    }




    @Deprecated
    public Long firstaccess;
    @Deprecated
    public Long lastaccess;
    @Deprecated
    public Long lastaccessTime;

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
    private void myKeyFirebase(String strKey, String strValue) {KanLogger.myKeyFirebase(strKey, strValue);}
    private void myLogFirebase(String strLog) {KanLogger.myLogFirebase(strLog);}
}