package com.driot.bookplayer.ebooks;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Keep;

@Keep
public class EbookItem implements Parcelable {

    public int gutendexId;
    public String title;
    public String authors;
    public String language;      // first language code, e.g. "en"
    public int downloadCount;
    public String coverUrl;
    public String epubUrl;

    // For future integration (once you store imported ebooks in DB)
    public boolean isImported;

    public EbookItem() {
        // Default constructor
    }

    protected EbookItem(Parcel in) {
        gutendexId = in.readInt();
        title = in.readString();
        authors = in.readString();
        language = in.readString();
        downloadCount = in.readInt();
        coverUrl = in.readString();
        epubUrl = in.readString();
        isImported = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(gutendexId);
        dest.writeString(title);
        dest.writeString(authors);
        dest.writeString(language);
        dest.writeInt(downloadCount);
        dest.writeString(coverUrl);
        dest.writeString(epubUrl);
        dest.writeByte((byte) (isImported ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<EbookItem> CREATOR = new Creator<EbookItem>() {
        @Override
        public EbookItem createFromParcel(Parcel in) {
            return new EbookItem(in);
        }

        @Override
        public EbookItem[] newArray(int size) {
            return new EbookItem[size];
        }
    };

    public boolean isImported() {
        return isImported;
    }

    public String toStringCrlf() {
        return "EbookItem{" +
                "gutendexId=" + gutendexId +
                "\n title='" + title + '\'' +
                "\n authors='" + authors + '\'' +
                "\n language='" + language + '\'' +
                "\n downloadCount=" + downloadCount +
                "\n coverUrl='" + coverUrl + '\'' +
                "\n epubUrl='" + epubUrl + '\'' +
                "\n isImported=" + isImported +
                '}';
    }
}
