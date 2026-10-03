package name.velikodniy.jcexpress.converter.translate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal clean-room reader of a compact-format CAP file (JCVM 3.1 Chapter 6), test scope only.
 *
 * <p>Reads exactly what the bytecode regression tests need: Header flags (6.4), constant pool
 * entries (6.8), the Method component exception handler table and method bodies (6.10), the
 * Reference Location component (6.12) and the method offsets of the Descriptor component
 * (6.14, used to slice method bodies out of the Method component).
 */
public record CapView(int headerFlags, List<CpInfo> constantPool, List<Handler> handlers,
                      List<MethodBody> methods, byte[] methodInfo,
                      List<Integer> byteIndexOffsets, List<Integer> byte2IndexOffsets) {

    /** A constant pool entry: tag and three info bytes (6.8). */
    public record CpInfo(int tag, int b1, int b2, int b3) {}

    /** An exception_handler_info entry with absolute Method component offsets (6.10.3). */
    public record Handler(int start, int end, boolean stopBit, int handlerOffset, int catchTypeIndex) {}

    /**
     * A method_info located through its method_descriptor_info (6.14), with the descriptor's
     * exception_handler_count and exception_handler_index.
     */
    public record MethodBody(int offset, int flags, int maxStack, int nargs, int maxLocals,
                             int codeOffset, byte[] code, int handlerCount, int handlerIndex) {
        /** Disassembled instruction lines of this method body. */
        public List<String> lines() {
            return JcvmDisassembler.lines(code);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MethodBody m && m.offset == offset && Arrays.equals(m.code, code);
        }

        @Override
        public int hashCode() {
            return 31 * offset + Arrays.hashCode(code);
        }

        @Override
        public String toString() {
            return "MethodBody@" + offset + lines();
        }
    }

    /** Parses a CAP file (JAR/ZIP container with compact components). */
    public static CapView parse(byte[] capFile) throws IOException {
        Map<String, byte[]> c = components(capFile);
        byte[] header = body(c.get("Header.cap"));
        byte[] method = body(c.get("Method.cap"));
        List<CpInfo> cp = constantPool(body(c.get("ConstantPool.cap")));
        List<Handler> handlers = handlers(method);
        List<MethodBody> methods = methods(method, body(c.get("Descriptor.cap")));
        byte[] refLoc = body(c.get("RefLocation.cap"));
        return new CapView(header[6] & 0xFF, cp, handlers, methods, method,
                offsets(refLoc, 0), offsets(refLoc, 2 + u2(refLoc, 0)));
    }

    /** All method bodies (non-abstract) disassembled, one list per method. */
    public List<List<String>> disassembledMethods() {
        return methods.stream().filter(m -> m.code().length > 0).map(MethodBody::lines).toList();
    }

    private static Map<String, byte[]> components(byte[] cap) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(cap))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName();
                map.put(n.substring(n.lastIndexOf('/') + 1), zis.readAllBytes());
            }
        }
        return map;
    }

    private static byte[] body(byte[] component) {
        return Arrays.copyOfRange(component, 3, component.length);
    }

    private static List<CpInfo> constantPool(byte[] b) {
        int count = u2(b, 0);
        List<CpInfo> cp = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int o = 2 + 4 * i;
            cp.add(new CpInfo(b[o] & 0xFF, b[o + 1] & 0xFF, b[o + 2] & 0xFF, b[o + 3] & 0xFF));
        }
        return cp;
    }

    private static List<Handler> handlers(byte[] m) {
        int count = m[0] & 0xFF;
        List<Handler> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int o = 1 + 8 * i;
            int start = u2(m, o);
            int bits = u2(m, o + 2);
            list.add(new Handler(start, start + (bits & 0x7FFF), (bits & 0x8000) != 0,
                    u2(m, o + 4), u2(m, o + 6)));
        }
        return list;
    }

    private static List<MethodBody> methods(byte[] m, byte[] d) {
        List<MethodBody> list = new ArrayList<>();
        int classCount = d[0] & 0xFF;
        int p = 1;
        for (int c = 0; c < classCount; c++) {
            int interfaceCount = d[p + 4] & 0xFF;
            int fieldCount = u2(d, p + 5);
            int methodCount = u2(d, p + 7);
            p += 9 + 2 * interfaceCount + 7 * fieldCount;
            for (int i = 0; i < methodCount; i++, p += 12) {
                int offset = u2(d, p + 2);
                int bytecodeCount = u2(d, p + 6);
                if (offset != 0) {
                    list.add(methodBody(m, offset, bytecodeCount, u2(d, p + 8), u2(d, p + 10)));
                }
            }
        }
        return list;
    }

    private static MethodBody methodBody(byte[] m, int offset, int bytecodeCount,
                                         int handlerCount, int handlerIndex) {
        int h0 = m[offset] & 0xFF;
        int flags = h0 >> 4;
        if ((flags & 0x8) != 0) {
            int code = offset + 4;
            return new MethodBody(offset, flags, m[offset + 1] & 0xFF, m[offset + 2] & 0xFF,
                    m[offset + 3] & 0xFF, code, Arrays.copyOfRange(m, code, code + bytecodeCount),
                    handlerCount, handlerIndex);
        }
        int code = offset + 2;
        return new MethodBody(offset, flags, h0 & 0x0F, (m[offset + 1] & 0xFF) >> 4,
                m[offset + 1] & 0x0F, code, Arrays.copyOfRange(m, code, code + bytecodeCount),
                handlerCount, handlerIndex);
    }

    /**
     * Whether every method_descriptor_info's exception_handler_index / exception_handler_count
     * (6.14) selects exactly the handlers whose active range starts in that method's bytecodes,
     * with index 0 when the count is 0.
     */
    public boolean descriptorHandlerRangesFollowSpec() {
        for (MethodBody mb : methods) {
            int end = mb.codeOffset() + mb.code().length;
            long inMethod = handlers.stream()
                    .filter(h -> h.start() >= mb.codeOffset() && h.start() < end).count();
            if (mb.handlerCount() != inMethod || (inMethod == 0 && mb.handlerIndex() != 0)) {
                return false;
            }
            for (int i = mb.handlerIndex(); i < mb.handlerIndex() + mb.handlerCount(); i++) {
                if (i >= handlers.size() || handlers.get(i).start() < mb.codeOffset()
                        || handlers.get(i).start() >= end) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Integer> offsets(byte[] refLoc, int countPos) {
        int count = u2(refLoc, countPos);
        List<Integer> abs = new ArrayList<>(count);
        int pos = 0;
        for (int i = 0; i < count; i++) {
            pos += refLoc[countPos + 2 + i] & 0xFF; // jump offsets; 255 = advance without entry
            if ((refLoc[countPos + 2 + i] & 0xFF) != 255) {
                abs.add(pos);
            }
        }
        return abs;
    }

    private static int u2(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }
}
