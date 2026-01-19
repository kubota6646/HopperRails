package com.github.kubota6646.hopperrails;

public class ColorCodeUtil {
    
    /**
     * Minecraftのカラーコードを削除する
     * @param message カラーコードを含む文字列
     * @return カラーコードを削除した文字列
     */
    public static String stripColorCodes(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("&a", "")
                     .replace("&e", "")
                     .replace("&c", "")
                     .replace("&7", "")
                     .replace("&0", "")
                     .replace("&1", "")
                     .replace("&2", "")
                     .replace("&3", "")
                     .replace("&4", "")
                     .replace("&5", "")
                     .replace("&6", "")
                     .replace("&8", "")
                     .replace("&9", "")
                     .replace("&b", "")
                     .replace("&d", "")
                     .replace("&f", "")
                     .replace("&k", "")
                     .replace("&l", "")
                     .replace("&m", "")
                     .replace("&n", "")
                     .replace("&o", "")
                     .replace("&r", "");
    }
}
