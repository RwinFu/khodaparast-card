package com.persiarobot.cardnfc;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public final class NfcCardService extends HostApduService {
    private final Type4Tag tag = new Type4Tag(CardConfig.URL);

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (!SessionState.active) {
            tag.reset();
            return new byte[] {(byte) 0x69, (byte) 0x85};
        }
        return tag.respond(commandApdu);
    }

    @Override
    public void onDeactivated(int reason) {
        tag.reset();
    }
}
