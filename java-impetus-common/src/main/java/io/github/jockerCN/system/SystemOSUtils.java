package io.github.jockerCN.system;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class SystemOSUtils {

    public static final String OS = System.getProperty("os.name", "unknown");
    public static final String OS_LOWERCASE = OS.toLowerCase(Locale.ROOT);


    public static boolean isWindows() {
        return getCurrentOSDetail() == OSDetail.WINDOWS;
    }

    public static boolean isUnix() {
        return getCurrentOS() == OSEnum.UNIX;
    }

    public static boolean isMac() {
        return getCurrentOSDetail() == OSDetail.MAC;
    }

    public static boolean isLinux() {
        return getCurrentOSDetail() == OSDetail.LINUX;
    }

    public static boolean isAndroid() {
        return getCurrentOSDetail() == OSDetail.ANDROID;
    }

    public static boolean isBsd() {
        OSDetail detail = getCurrentOSDetail();
        return detail == OSDetail.FREEBSD || detail == OSDetail.OPENBSD || detail == OSDetail.NETBSD;
    }

    public static OSDetail getCurrentOSDetail() {
        return detect(OS);
    }

    /** Classifies an OS name without reading global system properties. */
    public static OSDetail detect(String osName) {
        if (osName == null || osName.isBlank()) {
            return OSDetail.UNKNOWN;
        }
        String name = osName.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("android")) {
            return OSDetail.ANDROID;
        }
        if (name.startsWith("ios") || name.contains("iphone") || name.contains("ipad")) {
            return OSDetail.IOS;
        }
        if (name.startsWith("windows") || name.startsWith("win")) {
            return OSDetail.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin") || name.contains("os x")) {
            return OSDetail.MAC;
        }
        if (name.contains("linux")) {
            return OSDetail.LINUX;
        }
        if (name.contains("freebsd")) {
            return OSDetail.FREEBSD;
        }
        if (name.contains("openbsd")) {
            return OSDetail.OPENBSD;
        }
        if (name.contains("netbsd")) {
            return OSDetail.NETBSD;
        }
        if (name.contains("sunos") || name.contains("solaris")) {
            return OSDetail.SOLARIS;
        }
        if (name.contains("aix")) {
            return OSDetail.AIX;
        }
        if (name.contains("unix") || name.contains("hp-ux")) {
            return OSDetail.UNIX;
        }
        return OSDetail.UNKNOWN;
    }

    /** Backward-compatible broad family: Linux and other Unix-like systems map to UNIX. */
    public static OSEnum getCurrentOS() {
        return switch (getCurrentOSDetail()) {
            case WINDOWS -> OSEnum.WINDOWS;
            case MAC, IOS -> OSEnum.MAC;
            case LINUX, ANDROID, FREEBSD, OPENBSD, NETBSD, SOLARIS, AIX, UNIX -> OSEnum.UNIX;
            case UNKNOWN -> OSEnum.UNKNOWN;
        };
    }

    public enum OSDetail {
        WINDOWS, LINUX, MAC, ANDROID, IOS, FREEBSD, OPENBSD, NETBSD, SOLARIS, AIX, UNIX, UNKNOWN
    }

    @Getter
    @AllArgsConstructor
    public enum OSEnum {

        WINDOWS("Windows", ""),
        UNIX("Unix", ""),
        MAC("Mac", ""),
        UNKNOWN("Unknown", ""),
        ;


        private final String value;
        private final String desc;


    }

}
