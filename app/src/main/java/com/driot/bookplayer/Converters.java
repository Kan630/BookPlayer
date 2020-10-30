package com.driot.bookplayer;

import androidx.room.TypeConverter;

import java.sql.Date;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class Converters {
    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}
