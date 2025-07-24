package io.github.jockerCN.time;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class LocalDateUtils {

    @Nullable
    public static LocalDate stringToLocalDate(String time) {
        if (StringUtils.isEmpty(time)) {
            return null;
        }
        try {
            return LocalDate.parse(time, TimeFormatterTemplate.FORMATTER_YMD);
        } catch (Exception e) {
            try {
                return LocalDate.parse(time, TimeFormatterTemplate.FORMATTER_YMD_COMPACT);
            } catch (Exception ex) {
                try {
                    return LocalDate.parse(time, TimeFormatterTemplate.FORMATTER_YM);
                } catch (Exception exc) {
                    try {
                        return LocalDate.parse(time, TimeFormatterTemplate.FORMATTER_MD);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }
            }
        }
    }


    @Nullable
    public static LocalDateTime stringToLocalDateTime(String time) {
        if (StringUtils.isEmpty(time)) {
            return null;
        }
        try {
            return LocalDateTime.parse(time, TimeFormatterTemplate.FORMATTER_YMD_HMS);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(time, TimeFormatterTemplate.FORMATTER_YMD_THMS);
            } catch (Exception ex) {
                try {
                    return LocalDateTime.parse(time, TimeFormatterTemplate.FORMATTER_YMD_HMS_MILLIS);
                } catch (Exception exc) {
                    try {
                        return LocalDateTime.parse(time, TimeFormatterTemplate.FORMATTER_YMD_THMS_MILLIS);
                    } catch (Exception exce) {
                        try {
                            return LocalDateTime.parse(time, TimeFormatterTemplate.FORMATTER_YMD_THMS_MILLIS_Z);
                        } catch (Exception excep) {
                            return LocalDate.parse(time, TimeFormatterTemplate.FORMATTER_YMD).atStartOfDay();
                        }
                    }
                }
            }
        }
    }
}
