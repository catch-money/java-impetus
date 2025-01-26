package io.github.jockerCN.secret;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class SecureRandomCharacter {


    private static final String ORIGINAL_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String RANDOM_CHARACTERS;

    static {
        RANDOM_CHARACTERS = shuffleCharacters();
    }

    public static String shuffleCharacters() {
        byte[] charArray = SecureRandomCharacter.ORIGINAL_CHARACTERS.getBytes(StandardCharsets.UTF_8);
        for (int i = charArray.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            byte temp = charArray[i];
            charArray[i] = charArray[j];
            charArray[j] = temp;
        }
        return new String(charArray);
    }

    public static byte[] getDefaultRandomCharacters() {
        return RANDOM_CHARACTERS.getBytes(StandardCharsets.UTF_8);
    }


    private static void generateRandomSegment(int segmentLength, char[] characters,StringBuilder stringBuilder) {
        for (int i = 0; i < segmentLength; i++) {
            stringBuilder.append(characters[SECURE_RANDOM.nextInt(characters.length)]);
        }
    }

    public static String getRandomCharactersAsString(int segmentLength, int totalLength, char delimiter, char[] characters) {
        int totalSegments = totalLength / (segmentLength + 1);
        int remainingLength = totalLength % (segmentLength + 1);

        StringBuilder result = new StringBuilder(totalLength);

        for (int i = 0; i < totalSegments; i++) {
            generateRandomSegment(segmentLength, characters, result);
            if (result.length() < totalLength) {
                result.append(delimiter);
            }
        }

        if (remainingLength > 0) {
            generateRandomSegment(remainingLength, characters, result);
        }

        return result.toString();
    }

    public static byte[] getRandomCharactersAsByte(int segmentLength, int totalLength, char delimiter, char[] characters) {
        return getRandomCharactersAsString(segmentLength, totalLength, delimiter, characters).getBytes(StandardCharsets.UTF_8);
    }


    public static byte[] getDefaultRandomCharactersAsByte(int segmentLength, int totalLength) {
        return getDefaultRandomCharactersAsString(segmentLength, totalLength).getBytes(StandardCharsets.UTF_8);
    }

    public static String getDefaultRandomCharactersAsString(int segmentLength, int totalLength) {
        return getRandomCharactersAsString(segmentLength, totalLength,'-', RANDOM_CHARACTERS.toCharArray());
    }

}
