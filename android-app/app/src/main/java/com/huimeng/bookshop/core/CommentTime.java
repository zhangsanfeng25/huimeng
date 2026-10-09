package com.huimeng.bookshop.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Compare dates after applying the device zone, rather than slicing a UTC string. */
public final class CommentTime {
    private static final DateTimeFormatter TODAY=DateTimeFormatter.ofPattern("HH:mm",Locale.CHINA);
    private static final DateTimeFormatter THIS_YEAR=DateTimeFormatter.ofPattern("MM-dd",Locale.CHINA);
    private static final DateTimeFormatter OTHER_YEAR=DateTimeFormatter.ofPattern("yyyy-MM-dd",Locale.CHINA);
    private CommentTime(){}
    public static String format(String timestamp){return format(timestamp,Instant.now(),ZoneId.systemDefault());}
    public static String format(String timestamp,Instant now,ZoneId zone){
        if(timestamp==null||timestamp.isEmpty())return "";
        try{
            ZonedDateTime time=Instant.parse(timestamp).atZone(zone),current=now.atZone(zone);
            DateTimeFormatter formatter=OTHER_YEAR;
            if(time.toLocalDate().equals(current.toLocalDate()))formatter=TODAY;
            else if(time.getYear()==current.getYear())formatter=THIS_YEAR;
            return formatter.format(time);
        }catch(java.time.format.DateTimeParseException invalid){return timestamp;}
    }
}
