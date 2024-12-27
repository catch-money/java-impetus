package io.github.jockerCN;

import io.github.jockerCN.time.TimeFormatterTemplate;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class SerialNoUtils {

    public static String randomSerialNo(int digits) {
        String randomNumbers = RandomStringUtils.insecure().next(digits);
        String randomLetters = RandomStringUtils.insecure().nextAlphabetic(digits).toUpperCase();
        return String.join("", randomNumbers, randomLetters);
    }


    public static String randomSerialNo4() {
        String randomNumbers = RandomStringUtils.secure().next(4);
        String randomLetters = RandomStringUtils.insecure().nextAlphabetic(4).toUpperCase();
        return String.join("", randomNumbers, randomLetters);
    }

    public static String randomNumberSerialNo(int length) {
        return RandomStringUtils.insecure().next(length);
    }

    public static String getUserCode() {
        int i = ThreadLocalRandom.current().nextInt(1000, 9999);
        return String.join("", "U", getNowTime14Digit(), String.valueOf(i));
    }

    public static String getCode(String prefix) {
        int i = ThreadLocalRandom.current().nextInt(1000, 9999);
        return String.join("", prefix, getNowTime8Digit(), String.valueOf(i));
    }

    public static String get14Code(String prefix) {
        int i = ThreadLocalRandom.current().nextInt(1000, 9999);
        return String.join("", prefix, getNowTime14Digit(), String.valueOf(i));
    }

    public static String getCode(String prefix, String format) {
        int i = ThreadLocalRandom.current().nextInt(1000, 9999);
        return String.join("", prefix, getNowTimeDigit(format), String.valueOf(i));
    }

    private static String getNowTime14Digit() {
        return LocalDateTime.now().format(TimeFormatterTemplate.FORMATTER_YMD_HMS_COMPACT);
    }

    private static String getNowTime8Digit() {
        return LocalDateTime.now().format(TimeFormatterTemplate.formatter("yyMMdd"));
    }

    private static String getNowTimeDigit(String formatter) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(formatter));
    }

}
