package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.cap.BinaryWriter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constant pool of an export file (JCVM 3.1 §5.6): entries are numbered from zero in the order
 * they are first requested, and equal entries are shared.
 */
final class ConstantPool {

    private static final int CP_UTF8 = 1;
    private static final int CP_INTEGER = 3;
    private static final int CP_CLASSREF = 7;
    private static final int CP_PACKAGE = 13;
    private static final int MAX_ENTRIES = 0xFFFF;

    private final List<byte[]> entries = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();

    /** CONSTANT_Utf8_info (§5.6.4): modified UTF-8 as in class files. */
    int utf8(String value) {
        return intern("U:" + value, () -> {
            BinaryWriter w = new BinaryWriter();
            w.u1(CP_UTF8);
            w.bytes(modifiedUtf8(value));
            return w.toByteArray();
        });
    }

    /** CONSTANT_Classref_info (§5.6.2) of a fully qualified class or interface name. */
    int classref(String className) {
        int name = utf8(className);
        return intern("C:" + className, () -> new BinaryWriter().u1(CP_CLASSREF).u2(name).toByteArray());
    }

    /** CONSTANT_Integer_info (§5.6.3). */
    int integer(int value) {
        return intern("I:" + value, () -> new BinaryWriter().u1(CP_INTEGER).u4(value).toByteArray());
    }

    /** CONSTANT_Package_info (§5.6.1): flags, name, package version (minor first) and AID. */
    int pkg(String name, int flags, int major, int minor, byte[] aid) {
        int nameIndex = utf8(name);
        return intern("P:" + name, () -> new BinaryWriter().u1(CP_PACKAGE).u1(flags).u2(nameIndex)
                .u1(minor).u1(major).aidWithLength(aid).toByteArray());
    }

    /** Writes {@code constant_pool_count} and the entries. */
    void writeTo(BinaryWriter out) {
        out.u2(entries.size());
        for (byte[] e : entries) out.bytes(e);
    }

    private interface Encoder {
        byte[] encode();
    }

    private int intern(String key, Encoder encoder) {
        Integer existing = index.get(key);
        if (existing != null) return existing;
        if (entries.size() == MAX_ENTRIES) {
            throw new IllegalArgumentException("the export file constant pool exceeds 65535 entries (JCVM 3.1 §5.5)");
        }
        entries.add(encoder.encode());
        index.put(key, entries.size() - 1);
        return entries.size() - 1;
    }

    /** u2 length followed by the modified UTF-8 bytes (JVMS §4.4.7). */
    private static byte[] modifiedUtf8(String value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new DataOutputStream(bytes).writeUTF(value);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot encode '" + value + "' as CONSTANT_Utf8", e);
        }
    }
}
