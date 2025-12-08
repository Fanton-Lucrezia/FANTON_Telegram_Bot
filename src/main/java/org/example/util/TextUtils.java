package org.example.util;

/**
 * Utility class for text processing and formatting.
 */
public class TextUtils {

    /**
     * Escape HTML special characters to prevent XSS in Telegram messages.
     */
    public static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Truncate text to a maximum length with ellipsis.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Normalize search query (lowercase, trim, remove extra spaces).
     */
    public static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }

        return query.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "");
    }

    /**
     * Format a long text into multiple lines with a maximum width.
     */
    public static String wrapText(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String[] words = text.split("\\s+");
        int currentLineLength = 0;

        for (String word : words) {
            if (currentLineLength + word.length() + 1 > maxWidth) {
                result.append("\n");
                currentLineLength = 0;
            }

            if (currentLineLength > 0) {
                result.append(" ");
                currentLineLength++;
            }

            result.append(word);
            currentLineLength += word.length();
        }

        return result.toString();
    }

    /**
     * Check if two strings are similar (simple case-insensitive comparison).
     * For future: could implement Levenshtein distance for fuzzy matching.
     */
    public static boolean isSimilar(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return false;
        }

        return normalizeQuery(str1).equals(normalizeQuery(str2));
    }

    /**
     * Extract first N words from text.
     */
    public static String extractFirstWords(String text, int wordCount) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String[] words = text.split("\\s+");
        int count = Math.min(wordCount, words.length);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) result.append(" ");
            result.append(words[i]);
        }

        if (words.length > wordCount) {
            result.append("...");
        }

        return result.toString();
    }
}