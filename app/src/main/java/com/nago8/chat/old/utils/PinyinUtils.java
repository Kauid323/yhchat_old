package com.nago8.chat.old.utils;

public class PinyinUtils {
    private static final int[] BOUNDARIES = {
            0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE, 0xBBF7,
            0xBFA6, 0xC0AC, 0xC2E8, 0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA, 0xC8BB, 0xC8F6,
            0xCBFA, 0xCDDA, 0xCEF4, 0xD1B9, 0xD4D1
    };
    private static final char[] LETTERS = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
            'T', 'W', 'X', 'Y', 'Z'
    };

    /**
     * Gets the uppercase section initial letter ('A'-'Z' or '#') for a string.
     */
    public static String getSortLetter(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "#";
        }
        String trimmed = input.trim();
        char firstChar = trimmed.charAt(0);

        // Check if first char is English letter
        if ((firstChar >= 'a' && firstChar <= 'z') || (firstChar >= 'A' && firstChar <= 'Z')) {
            return String.valueOf(Character.toUpperCase(firstChar));
        }

        // Check Chinese character Pinyin initial
        char pinyinLetter = getChineseInitial(firstChar);
        if (pinyinLetter >= 'A' && pinyinLetter <= 'Z') {
            return String.valueOf(pinyinLetter);
        }

        return "#";
    }

    private static char getChineseInitial(char ch) {
        try {
            byte[] bytes = String.valueOf(ch).getBytes("GB2312");
            if (bytes.length < 2) {
                return '#';
            }
            int code = ((bytes[0] + 256) << 8) + (bytes[1] + 256);
            for (int i = 0; i < BOUNDARIES.length; i++) {
                if (code < BOUNDARIES[i]) {
                    if (i == 0) return '#';
                    return LETTERS[i - 1];
                }
            }
            if (code >= BOUNDARIES[BOUNDARIES.length - 1] && code <= 0xD7FA) {
                return LETTERS[LETTERS.length - 1];
            }
        } catch (Exception ignored) {
        }
        return '#';
    }
}
