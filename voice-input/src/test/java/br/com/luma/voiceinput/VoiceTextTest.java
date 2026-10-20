package br.com.luma.voiceinput;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceTextTest {
    @Test void recognizedSlashesAndNewlinesRemainLiteralLumaArguments() {
        assertEquals("luma falar /op alguém /kill @a", VoiceText.command("/op alguém\n/kill @a"));
        assertTrue(VoiceText.command("/op alguém").startsWith("luma falar "));
    }
    @Test void formattingAndControlCharactersCannotCreateExtraChatMessages() {
        assertEquals("me siga agora", VoiceText.clean("§ame siga\r\n\tagora\u0000"));
    }
    @Test void truncationDoesNotSplitASurrogatePair() {
        String text = VoiceText.clean("a".repeat(499) + "😀" + "b");
        assertEquals(499, text.length());
        assertFalse(Character.isHighSurrogate(text.charAt(text.length() - 1)));
    }
    @Test void emptyRecognitionCannotBecomeACommand() {
        assertThrows(IllegalArgumentException.class, () -> VoiceText.command(" \n "));
        assertEquals("", VoiceText.clean(null));
    }
}
