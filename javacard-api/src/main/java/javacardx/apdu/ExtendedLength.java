package javacardx.apdu;

/**
 * Marker interface: an applet class that implements it declares that it accepts extended-length APDUs
 * (ISO/IEC 7816-4, Lc and Le of up to 65535 bytes). Such an applet reads the command data with
 * {@code APDU.getOffsetCdata()} and {@code APDU.getIncomingLength()}, because the data no longer starts at
 * offset 5.
 */
public interface ExtendedLength {
}
