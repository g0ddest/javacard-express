package com.example.vault;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import org.globalplatform.GPSystem;
import org.globalplatform.SecureChannel;

/**
 * Keeps a value that only an authenticated off-card entity may write: the applet hands INITIALIZE UPDATE and
 * EXTERNAL AUTHENTICATE to its Security Domain, has the secure channel check its commands and protect its
 * responses, and keeps a life cycle state of its own (GlobalPlatform Card API, package org.globalplatform).
 */
public class VaultApplet extends Applet {

    private static final byte INS_INITIALIZE_UPDATE = (byte) 0x50;
    private static final byte INS_EXTERNAL_AUTHENTICATE = (byte) 0x82;
    private static final byte INS_PUT = (byte) 0x10;
    private static final byte INS_GET = (byte) 0x12;
    private static final byte INS_STATE = (byte) 0x14;
    /** The bit of the class byte that tells GlobalPlatform secure messaging (GPCS v2.3.1 11.1.4). */
    private static final byte CLA_SECURE_MESSAGING = (byte) 0x04;
    /** Application specific life cycle state: a value was stored. */
    private static final byte STATE_FILLED = (byte) 0x0F;

    private final byte[] value = new byte[16];
    private short length;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new VaultApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void deselect() {
        GPSystem.getSecureChannel().resetSecurity();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        SecureChannel channel = GPSystem.getSecureChannel();
        byte ins = buffer[ISO7816.OFFSET_INS];
        if (ins == INS_INITIALIZE_UPDATE || ins == INS_EXTERNAL_AUTHENTICATE) {
            apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, channel.processSecurity(apdu));
            return;
        }
        short data = receive(apdu, channel);
        send(apdu, channel, execute(ins, buffer, data, channel));
    }

    /**
     * Receives the data of a command that has some (PUT, and every command with secure messaging, which carries at
     * least the MAC). A command with secure messaging, and every command of a session that requires C-MAC, goes
     * through {@code unwrap}, which checks it and removes its protection; in such a session it refuses a command
     * without protection.
     *
     * @return the length of the plain command data
     */
    private short receive(APDU apdu, SecureChannel channel) {
        byte[] buffer = apdu.getBuffer();
        boolean secureMessaging = (buffer[ISO7816.OFFSET_CLA] & CLA_SECURE_MESSAGING) != 0;
        short received = 0;
        if (secureMessaging || buffer[ISO7816.OFFSET_INS] == INS_PUT) {
            received = apdu.setIncomingAndReceive();
        }
        if (!secureMessaging && (channel.getSecurityLevel() & SecureChannel.C_MAC) == 0) {
            return received;
        }
        short command = channel.unwrap(buffer, (short) 0, (short) (ISO7816.OFFSET_CDATA + received));
        return (short) (command - ISO7816.OFFSET_CDATA);
    }

    /**
     * Executes the applet's own command and writes its response data to the start of the buffer.
     *
     * @return the length of the response data
     */
    private short execute(byte ins, byte[] buffer, short data, SecureChannel channel) {
        switch (ins) {
            case INS_PUT:
                put(buffer, data, channel);
                return 0;
            case INS_GET:
                return Util.arrayCopyNonAtomic(value, (short) 0, buffer, (short) 0, length);
            case INS_STATE:
                buffer[0] = GPSystem.getCardContentState();
                return 1;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
                return 0;
        }
    }

    /** Stores the value; only an authenticated off-card entity may do that. */
    private void put(byte[] buffer, short data, SecureChannel channel) {
        if ((channel.getSecurityLevel() & SecureChannel.AUTHENTICATED) == 0) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        if (data > (short) value.length) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        Util.arrayCopy(buffer, ISO7816.OFFSET_CDATA, value, (short) 0, data);
        length = data;
        GPSystem.setCardContentState(STATE_FILLED);
    }

    /**
     * Sends the response that starts at offset 0 of the APDU buffer. In a session with response protection the
     * secure channel protects it first: {@code wrap} takes the response data followed by the status bytes and
     * returns the length of what to send. Such a session protects every successful response, also one without data.
     */
    private void send(APDU apdu, SecureChannel channel, short response) {
        byte[] buffer = apdu.getBuffer();
        short length = response;
        if ((channel.getSecurityLevel() & SecureChannel.R_MAC) != 0) {
            Util.setShort(buffer, response, ISO7816.SW_NO_ERROR);
            length = channel.wrap(buffer, (short) 0, (short) (response + 2));
        }
        if (length > 0) {
            apdu.setOutgoingAndSend((short) 0, length);
        }
    }
}
