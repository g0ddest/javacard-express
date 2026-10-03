package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.pcsc.PcscSession;

/**
 * What a {@link PcscSession} reports about its connection, read without sending a command.
 *
 * @param options  the session's options (protocol request, exclusive access)
 * @param protocol the negotiated protocol, {@code T=0} or {@code T=1}
 * @param atr      the card's Answer To Reset, hex
 */
public record PcscView(PcscSession.Options options, String protocol, String atr) {
}
