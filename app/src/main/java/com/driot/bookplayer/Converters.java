package com.driot.bookplayer;

import androidx.room.TypeConverter;

import java.sql.Date;
import java.sql.Time;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class Converters {

    @TypeConverter
    public static Time fromTimestamp2(Long value) {
        return value == null ? null : new Time(value);
    }

    @TypeConverter
    public static Long dateToTimestamp2(Time time) {
        return time == null ? null : time.getTime();
    }
}

/**
 original :
 @TypeConverter
 public static Date fromTimestamp(Long value) {
 return value == null ? null : new Date(value);
 }

 @TypeConverter
 public static Long dateToTimestamp(Date date) {
 return date == null ? null : date.getTime();
 }
 */