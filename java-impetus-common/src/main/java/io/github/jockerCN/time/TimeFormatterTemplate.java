package io.github.jockerCN.time;

import java.time.format.DateTimeFormatter;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class TimeFormatterTemplate {

    // 常见时间格式字符串
    public static final String FORMAT_YM = "yyyy-MM";
    public static final String FORMAT_YM_CHINESE = "yyyy年MM月";
    public static final String FORMAT_YMD = "yyyy-MM-dd";
    public static final String FORMAT_MD = "MM-dd";
    public static final String FORMAT_MD_CHINESE = "MM月dd日";
    public static final String FORMAT_YMD_COMPACT = "yyyyMMdd";
    public static final String FORMAT_YMDHMS_COMPACT = "yyyyMMddHHmmss";
    public static final String FORMAT_YMDHMS = "yyyy-MM-dd HH:mm:ss";
    public static final String FORMAT_YMDHMS_MILLIS = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String FORMAT_YMDHMS_MILLIS_SHORT = "yyyy-MM-dd HH:mm:ss.S";
    public static final String FORMAT_YMDTHMS = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String FORMAT_YMDTHMS_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String FORMAT_YMDTHMS_MILLIS_Z = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";


    public static final DateTimeFormatter FORMATTER_MD_CHINESE = DateTimeFormatter.ofPattern(FORMAT_MD_CHINESE);
    public static final DateTimeFormatter FORMATTER_MD = DateTimeFormatter.ofPattern(FORMAT_MD);
    public static final DateTimeFormatter FORMATTER_YM = DateTimeFormatter.ofPattern(FORMAT_YM);
    public static final DateTimeFormatter FORMATTER_YM_CHINESE = DateTimeFormatter.ofPattern(FORMAT_YM_CHINESE);
    public static final DateTimeFormatter FORMATTER_YMD = DateTimeFormatter.ofPattern(FORMAT_YMD);
    public static final DateTimeFormatter FORMATTER_YMD_COMPACT = DateTimeFormatter.ofPattern(FORMAT_YMD_COMPACT);
    public static final DateTimeFormatter FORMATTER_YMD_HMS_COMPACT = DateTimeFormatter.ofPattern(FORMAT_YMDHMS_COMPACT);
    public static final DateTimeFormatter FORMATTER_YMD_HMS = DateTimeFormatter.ofPattern(FORMAT_YMDHMS);
    public static final DateTimeFormatter FORMATTER_YMD_HMS_MILLIS = DateTimeFormatter.ofPattern(FORMAT_YMDHMS_MILLIS);
    public static final DateTimeFormatter FORMATTER_YMD_HMS_MILLIS_SHORT = DateTimeFormatter.ofPattern(FORMAT_YMDHMS_MILLIS_SHORT);
    public static final DateTimeFormatter FORMATTER_YMD_THMS = DateTimeFormatter.ofPattern(FORMAT_YMDTHMS);
    public static final DateTimeFormatter FORMATTER_YMD_THMS_MILLIS = DateTimeFormatter.ofPattern(FORMAT_YMDTHMS_MILLIS);
    public static final DateTimeFormatter FORMATTER_YMD_THMS_MILLIS_Z = DateTimeFormatter.ofPattern(FORMAT_YMDTHMS_MILLIS_Z);


    public static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern);
    }
}