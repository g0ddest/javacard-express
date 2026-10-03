package name.velikodniy.jcexpress.server;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payload of {@link Protocol#CMD_INSTALL}.
 *
 * <pre>
 * u1  aidLength,  aid
 * u2  nameLength, applet class binary name (UTF-8)
 * s4  classLength, applet class file
 * s4  extraCount, extraCount x { u2 nameLength, name, s4 length, class file }
 * u2  paramsLength, install parameters   (optional: absent in requests of clients before 0.3.0)
 * </pre>
 *
 * <p>The install parameters are passed unchanged to {@code Applet.install(bArray, 0, bLength)}: the client builds
 * the Java Card layout {@code [Li][instance AID][Lc][control info][La][applet data]}, the server only enforces the
 * Java Card API limit {@code bLength <= 127}, so the length always fits the {@code byte} parameter.</p>
 *
 * <p>Every length is checked against the remaining payload, so a malformed request is reported as an
 * {@link IllegalArgumentException} instead of an arbitrary runtime error.</p>
 *
 * @param aid           instance AID bytes
 * @param appletClass   binary name of the applet class
 * @param classes       class files by binary name (applet class first) that the server must define
 * @param installParams the {@code bArray} passed to {@code Applet.install} (at most 127 bytes)
 */
record InstallRequest(byte[] aid, String appletClass, Map<String, byte[]> classes, byte[] installParams) {

    /**
     * Validates the install parameter limit. Java Card API, {@code Applet.install(byte[] bArray, short bOffset,
     * byte bLength)}: "The maximum value of bLength is 127."
     */
    InstallRequest {
        if (installParams.length > Protocol.MAX_INSTALL_PARAMS) {
            throw new IllegalArgumentException("Install parameters too long: " + installParams.length
                    + " bytes (max " + Protocol.MAX_INSTALL_PARAMS + ", the Java Card limit of Applet.install bLength)");
        }
    }

    /**
     * Parses and validates an install payload.
     *
     * @param payload the request payload
     * @return the request
     * @throws IllegalArgumentException if the payload is malformed or the install parameters exceed 127 bytes
     */
    static InstallRequest parse(byte[] payload) {
        ByteBuffer in = ByteBuffer.wrap(payload);
        try {
            byte[] aid = bytes(in, Byte.toUnsignedInt(in.get()), "AID");
            String appletClass = name(in);
            Map<String, byte[]> classes = new LinkedHashMap<>();
            classes.put(appletClass, bytes(in, in.getInt(), "class file of " + appletClass));
            int extra = in.getInt();
            if (extra < 0 || extra > in.remaining()) {
                throw malformed("invalid class count " + extra);
            }
            for (int i = 0; i < extra; i++) {
                String name = name(in);
                classes.put(name, bytes(in, in.getInt(), "class file of " + name));
            }
            byte[] params = in.hasRemaining() ? bytes(in, Short.toUnsignedInt(in.getShort()), "install parameters")
                    : new byte[0];
            if (in.hasRemaining()) {
                throw malformed(in.remaining() + " unexpected trailing bytes");
            }
            return new InstallRequest(aid, appletClass, Collections.unmodifiableMap(classes), params);
        } catch (BufferUnderflowException e) {
            throw malformed("truncated payload (" + payload.length + " bytes)");
        }
    }

    private static String name(ByteBuffer in) {
        byte[] name = bytes(in, Short.toUnsignedInt(in.getShort()), "class name");
        if (name.length == 0) {
            throw malformed("empty class name");
        }
        return new String(name, StandardCharsets.UTF_8);
    }

    private static byte[] bytes(ByteBuffer in, int length, String what) {
        if (length < 0 || length > in.remaining()) {
            throw malformed(what + ": length " + length + " exceeds the " + in.remaining() + " remaining bytes");
        }
        byte[] b = new byte[length];
        in.get(b);
        return b;
    }

    private static IllegalArgumentException malformed(String detail) {
        return new IllegalArgumentException("Malformed INSTALL request: " + detail);
    }
}
