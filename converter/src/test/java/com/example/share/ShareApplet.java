package com.example.share;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.AppletEvent;
import javacard.framework.ISO7816;
import javacard.framework.MultiSelectable;
import javacard.framework.Shareable;
import javacard.framework.Util;

/** Shareable interface hierarchy plus interfaces from an imported package. */
public class ShareApplet extends Applet implements IExt, MultiSelectable, AppletEvent, AStatus {
    private short value;

    ShareApplet() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ShareApplet();
    }

    @Override
    public short get() {
        return value;
    }

    @Override
    public void set(short v) {
        value = v;
    }

    @Override
    public short status() {
        return (short) (value + 1);
    }

    @Override
    public boolean select(boolean appInstAlreadyActive) {
        return true;
    }

    @Override
    public void deselect(boolean appInstStillActive) {
        value = 0;
    }

    @Override
    public void uninstall() {
        value = 0;
    }

    @Override
    public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        return this;
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        IExt ext = this;
        ext.set(buf[ISO7816.OFFSET_P1]);
        short r = ext.get();
        IBase base = this;
        r += base.get();
        AStatus st = this;
        r += st.status();
        Util.setShort(buf, (short) 0, r);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
