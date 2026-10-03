package name.velikodniy.jcexpress.container;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Encodes the payload of {@link Protocol#CMD_INSTALL}:
 * <pre>
 * u1 aidLength, aid
 * u2 nameLength, applet class binary name (UTF-8), s4 length, class file
 * s4 extraCount, extraCount x { u2 nameLength, name, s4 length, class file }
 * u2 paramsLength, install parameters
 * </pre>
 */
final class InstallPayload {

    private InstallPayload() {
    }

    /**
     * Encodes an install request.
     *
     * @param aid         instance AID
     * @param appletClass binary name of the applet class
     * @param classes     class files by binary name, containing {@code appletClass}
     * @param params      install parameters (at most {@link Protocol#MAX_INSTALL_PARAMS} bytes)
     * @return the payload
     * @throws IllegalArgumentException if the request does not fit into one frame
     */
    static byte[] encode(byte[] aid, String appletClass, Map<String, byte[]> classes, byte[] params) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(aid.length);
            out.write(aid);
            writeClass(out, appletClass, classes.get(appletClass));
            out.writeInt(classes.size() - 1);
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                if (!entry.getKey().equals(appletClass)) {
                    writeClass(out, entry.getKey(), entry.getValue());
                }
            }
            out.writeShort(params.length);
            out.write(params);
            return checkSize(bytes.toByteArray(), appletClass, classes.size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeClass(DataOutputStream out, String name, byte[] classFile) throws IOException {
        byte[] encodedName = name.getBytes(StandardCharsets.UTF_8);
        out.writeShort(encodedName.length);
        out.write(encodedName);
        out.writeInt(classFile.length);
        out.write(classFile);
    }

    private static byte[] checkSize(byte[] payload, String appletClass, int classCount) {
        if (payload.length > Protocol.MAX_PAYLOAD) {
            throw new IllegalArgumentException("Cannot install " + appletClass + ": its " + classCount
                    + " classes take " + payload.length + " bytes, more than the simulator accepts in one request ("
                    + Protocol.MAX_PAYLOAD + ")");
        }
        return payload;
    }
}
