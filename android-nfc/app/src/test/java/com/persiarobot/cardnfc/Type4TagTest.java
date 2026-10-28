package com.persiarobot.cardnfc;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class Type4TagTest {
    private static final byte[] SELECT_APP = hex("00A4040007D276000085010100");
    private static final byte[] SELECT_CC = hex("00A4000C02E103");
    private static final byte[] SELECT_NDEF = hex("00A4000C02E104");

    @Test
    public void readerCanSelectFilesAndReadTheWebsiteUri() {
        Type4Tag tag = new Type4Tag(CardConfig.URL);
        assertArrayEquals(hex("6985"), tag.respond(hex("00B0000002")));
        assertArrayEquals(hex("9000"), tag.respond(SELECT_APP));
        assertArrayEquals(hex("9000"), tag.respond(SELECT_CC));
        assertArrayEquals(hex("000F20003B00340406E1047FFF00FF9000"),
                tag.respond(hex("00B000000F")));
        assertArrayEquals(hex("9000"), tag.respond(SELECT_NDEF));

        byte[] lengthResponse = tag.respond(hex("00B0000002"));
        int ndefLength = ((lengthResponse[0] & 0xff) << 8) | (lengthResponse[1] & 0xff);
        assertArrayEquals(hex("9000"), Arrays.copyOfRange(lengthResponse, 2, 4));
        byte[] messageResponse = tag.respond(hex("00B0000200"));
        byte[] ndef = Arrays.copyOf(messageResponse, messageResponse.length - 2);
        assertEquals(ndefLength, ndef.length);
        assertEquals(0xd1, ndef[0] & 0xff);
        assertEquals(1, ndef[1] & 0xff);
        assertEquals(ndef.length - 4, ndef[2] & 0xff);
        assertEquals('U', ndef[3]);
        assertEquals(0x04, ndef[4]);
        String uri = "https://" + new String(ndef, 5, ndef.length - 5, StandardCharsets.UTF_8);
        assertEquals(CardConfig.URL, uri);
        assertArrayEquals(hex("9000"), Arrays.copyOfRange(messageResponse, ndefLength, ndefLength + 2));

        byte[] firstChunk = tag.respond(hex("00B0000205"));
        byte[] secondChunk = tag.respond(hex("00B0000700"));
        assertArrayEquals(Arrays.copyOfRange(ndef, 0, 5), Arrays.copyOf(firstChunk, 5));
        assertArrayEquals(Arrays.copyOfRange(ndef, 5, ndef.length),
                Arrays.copyOf(secondChunk, secondChunk.length - 2));
    }

    @Test
    public void invalidAndWriteCommandsAreRejected() {
        Type4Tag tag = new Type4Tag(CardConfig.URL);
        assertArrayEquals(hex("6700"), tag.respond(null));
        assertArrayEquals(hex("6700"), tag.respond(hex("00A4040007D276")));
        assertArrayEquals(hex("6E00"), tag.respond(hex("80A4040000")));
        assertArrayEquals(hex("9000"), tag.respond(SELECT_APP));
        assertArrayEquals(hex("9000"), tag.respond(SELECT_NDEF));
        assertArrayEquals(hex("6D00"), tag.respond(hex("00D600000100")));
        assertArrayEquals(hex("6B00"), tag.respond(hex("00B07FFF01")));
        assertArrayEquals(hex("6A82"), tag.respond(hex("00A4000C02FFFF")));
        assertArrayEquals(hex("6985"), tag.respond(hex("00B0000002")));
    }

    @Test
    public void newSessionRequiresSelectingTheNdefApplicationAgain() {
        Type4Tag tag = new Type4Tag(CardConfig.URL);
        assertArrayEquals(hex("9000"), tag.respond(SELECT_APP));
        assertArrayEquals(hex("9000"), tag.respond(SELECT_NDEF));
        tag.reset();
        assertArrayEquals(hex("6985"), tag.respond(hex("00B0000002")));
        assertArrayEquals(hex("9000"), tag.respond(SELECT_APP));
        assertArrayEquals(hex("6A82"), tag.respond(hex("00A404000701020304050607")));
        assertArrayEquals(hex("6985"), tag.respond(SELECT_NDEF));
    }

    @Test
    public void uriMustBeHttpsAndFitInAShortRecord() {
        assertThrows(IllegalArgumentException.class, () -> new Type4Tag("http://example.com"));
        char[] longPath = new char[255];
        Arrays.fill(longPath, 'x');
        assertThrows(IllegalArgumentException.class, () -> new Type4Tag("https://" + new String(longPath)));
    }

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
