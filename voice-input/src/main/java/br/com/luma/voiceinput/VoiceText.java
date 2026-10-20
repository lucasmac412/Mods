package br.com.luma.voiceinput;

/** Only a bounded literal argument is ever sent to the Luma command. */
public final class VoiceText {
    private VoiceText() {}

    public static String clean(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("§.", "").replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        int end = Math.min(500, cleaned.length());
        if (end < cleaned.length() && Character.isHighSurrogate(cleaned.charAt(end - 1))) end--;
        return cleaned.substring(0, end);
    }

    public static String command(String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) throw new IllegalArgumentException("Empty transcript");
        return "luma falar " + cleaned;
    }
}
