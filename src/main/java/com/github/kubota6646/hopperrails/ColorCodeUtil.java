package com.github.kubota6646.hopperrails;

import java.util.regex.Pattern;

public class ColorCodeUtil {
    
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    
    /**
     * Minecraftのカラーコードを削除する
     * @param message カラーコードを含む文字列
     * @return カラーコードを削除した文字列
     */
    public static String stripColorCodes(String message) {
        if (message == null) {
            return "";
        }
        return COLOR_CODE_PATTERN.matcher(message).replaceAll("");
    }
}
