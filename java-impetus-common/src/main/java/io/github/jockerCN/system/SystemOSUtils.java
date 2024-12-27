package io.github.jockerCN.system;


import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class SystemOSUtils {

    public static final String OS = System.getProperty("os.name");
    public static final String OS_LOWERCASE = OS.toLowerCase();


    public static boolean isWindows() {
        return OS_LOWERCASE.contains("windows") || OS_LOWERCASE.contains("win");
    }

    public static boolean isUnix() {
        return OS_LOWERCASE.contains("linux") || OS_LOWERCASE.contains("unix");
    }

    public static boolean isMac() {
        return OS_LOWERCASE.contains("mac");
    }


    public static OSEnum getCurrentOS() {
        if (isWindows()) {
            return OSEnum.WINDOWS;
        }
        if (isUnix()) {
            return OSEnum.UNIX;
        }
        if (isMac()) {
            return OSEnum.MAC;
        }
        return OSEnum.UNKNOWN;
    }

    @Getter
    @AllArgsConstructor
    public enum OSEnum implements BaseEnum<OSEnum, String, String> {

        WINDOWS("Windows", ""),
        UNIX("Unix", ""),
        MAC("Mac", ""),
        UNKNOWN("Unknown", ""),
        ;


        private final String value;
        private final String desc;


    }

}
