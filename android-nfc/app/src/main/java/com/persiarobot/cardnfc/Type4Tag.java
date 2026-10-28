package com.persiarobot.cardnfc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Read-only NFC Forum Type 4 tag containing one NDEF URI record. */
final class Type4Tag {
    private static final byte[] NDEF_AID = hex("D2760000850101");
    // Type 4 mapping v2.0, free reads, no writes, NDEF file E104.
    private static final byte[] CAPABILITY_CONTAINER = hex("000F20003B00340406E1047FFF00FF");
    private static final byte[] OK = hex("9000");
    private static final byte[] NOT_FOUND = hex("6A82");
    private static final byte[] WRONG_LENGTH = hex("6700");
    private static final byte[] WRONG_OFFSET = hex("6B00");
    private static final byte[] NOT_SELECTED = hex("6985");
    private static final byte[] UNSUPPORTED_INSTRUCTION = hex("6D00");
    private static final byte[] UNSUPPORTED_CLASS = hex("6E00");

    private enum FileSelection { NONE, CAPABILITY_CONTAINER, NDEF }

    private final byte[] ndefFile;
    private boolean applicationSelected;
    private FileSelection selectedFile = FileSelection.NONE;

    Type4Tag(String url) {
        byte[] message = uriRecord(url);
        ndefFile = new byte[message.length + 2];
        ndefFile[0] = (byte) (message.length >>> 8);
        ndefFile[1] = (byte) message.length;
        System.arraycopy(message, 0, ndefFile, 2, message.length);
    }

    byte[] respond(byte[] command) {
        if (command == null || command.length < 4) return WRONG_LENGTH.clone();
        if (command[0] != 0) return UNSUPPORTED_CLASS.clone();

        int instruction = command[1] & 0xff;
        if (instruction == 0xa4) return select(command);
        if (!applicationSelected) return NOT_SELECTED.clone();
        if (instruction == 0xb0) return readBinary(command);
        return UNSUPPORTED_INSTRUCTION.clone();
    }

    void reset() {
        applicationSelected = false;
        selectedFile = FileSelection.NONE;
    }

    private byte[] select(byte[] command) {
        if (command.length < 5) return WRONG_LENGTH.clone();
        int length = command[4] & 0xff;
        if (command.length < 5 + length) return WRONG_LENGTH.clone();
        int mode = command[2] & 0xff;

        if (mode == 0x04) {
            reset();
            if (length == NDEF_AID.length
                    && Arrays.equals(Arrays.copyOfRange(command, 5, 5 + length), NDEF_AID)) {
                applicationSelected = true;
                return OK.clone();
            }
            return NOT_FOUND.clone();
        }

        if (!applicationSelected) return NOT_SELECTED.clone();
        selectedFile = FileSelection.NONE;
        if (mode != 0x00 || length != 2) return NOT_FOUND.clone();
        int id = ((command[5] & 0xff) << 8) | (command[6] & 0xff);
        if (id == 0xe103) selectedFile = FileSelection.CAPABILITY_CONTAINER;
        if (id == 0xe104) selectedFile = FileSelection.NDEF;
        return selectedFile == FileSelection.NONE ? NOT_FOUND.clone() : OK.clone();
    }

    private byte[] readBinary(byte[] command) {
        if (command.length < 5) return WRONG_LENGTH.clone();
        byte[] file;
        if (selectedFile == FileSelection.CAPABILITY_CONTAINER) {
            file = CAPABILITY_CONTAINER;
        } else if (selectedFile == FileSelection.NDEF) {
            file = ndefFile;
        } else {
            return NOT_SELECTED.clone();
        }
        int offset = ((command[2] & 0x7f) << 8) | (command[3] & 0xff);
        if (offset > file.length) return WRONG_OFFSET.clone();
        int requested = command[4] & 0xff;
        if (requested == 0) requested = 256;
        int end = Math.min(offset + requested, file.length);
        byte[] result = new byte[end - offset + OK.length];
        System.arraycopy(file, offset, result, 0, end - offset);
        System.arraycopy(OK, 0, result, end - offset, OK.length);
        return result;
    }

    private static byte[] uriRecord(String url) {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("The NFC link must use HTTPS");
        }
        byte[] suffix = url.substring("https://".length()).getBytes(StandardCharsets.UTF_8);
        if (suffix.length > 254) throw new IllegalArgumentException("NFC link is too long");
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(0xd1); // MB, ME, short record, well-known type.
        message.write(1); // Type length.
        message.write(suffix.length + 1); // URI prefix byte plus URL.
        message.write('U');
        message.write(0x04); // NDEF URI prefix: https://
        message.write(suffix, 0, suffix.length);
        return message.toByteArray();
    }

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
