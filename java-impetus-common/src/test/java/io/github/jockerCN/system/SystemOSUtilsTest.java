package io.github.jockerCN.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemOSUtilsTest {

    @Test
    void distinguishesDarwinFromWindowsAndRecognizesUnixFamilies() {
        assertEquals(SystemOSUtils.OSDetail.MAC, SystemOSUtils.detect("Darwin"));
        assertEquals(SystemOSUtils.OSDetail.WINDOWS, SystemOSUtils.detect("Windows 11"));
        assertEquals(SystemOSUtils.OSDetail.LINUX, SystemOSUtils.detect("Linux"));
        assertEquals(SystemOSUtils.OSDetail.ANDROID, SystemOSUtils.detect("Android Linux"));
        assertEquals(SystemOSUtils.OSDetail.FREEBSD, SystemOSUtils.detect("FreeBSD"));
        assertEquals(SystemOSUtils.OSDetail.OPENBSD, SystemOSUtils.detect("OpenBSD"));
        assertEquals(SystemOSUtils.OSDetail.NETBSD, SystemOSUtils.detect("NetBSD"));
        assertEquals(SystemOSUtils.OSDetail.SOLARIS, SystemOSUtils.detect("SunOS"));
        assertEquals(SystemOSUtils.OSDetail.AIX, SystemOSUtils.detect("AIX"));
        assertEquals(SystemOSUtils.OSDetail.IOS, SystemOSUtils.detect("iPhone OS"));
        assertEquals(SystemOSUtils.OSDetail.UNKNOWN, SystemOSUtils.detect(null));
        assertEquals(SystemOSUtils.OSDetail.UNKNOWN, SystemOSUtils.detect("OtherOS"));
    }
}
