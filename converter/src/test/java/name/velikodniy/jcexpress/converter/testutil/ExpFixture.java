package name.velikodniy.jcexpress.converter.testutil;

import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Test-only writer of export files, written directly from JCVM 3.1 Chapter 5 and deliberately
 * independent of the production {@code ExportFileWriter}.
 *
 * <p>It produces spec-conformant fixtures (format 2.1 or 2.3) for reader and import tests,
 * so that no Oracle-generated export file has to be committed. The constant pool starts at
 * index 0 (§5.5), tokens are u1 (§5.7-§5.9), fields carry an attributes table (§5.8) and
 * format 2.3 adds {@code referenced_packages} (§5.5) and
 * {@code CAP22_inheritable_public_method_token_count} (§5.7).
 */
public final class ExpFixture {

    /** A package entry (CONSTANT_Package_info, JCVM 3.1 §5.6.1). */
    public record Pkg(String name, int flags, int major, int minor, String aidHex) {}

    /** A method_info entry (JCVM 3.1 §5.9). */
    public record Method(int token, int flags, String name, String descriptor) {}

    /** A field_info entry (JCVM 3.1 §5.8); {@code constant} adds a ConstantValue attribute. */
    public record Field(int token, int flags, String name, String descriptor, Integer constant) {}

    /** A class_info entry (JCVM 3.1 §5.7). */
    public record Cls(int token, int flags, String name, List<String> supers, List<String> interfaces,
                      List<Field> fields, List<Method> methods, int cap22InheritableCount) {}

    private final List<Object> cp = new ArrayList<>();

    private ExpFixture() {}

    /**
     * Writes an export file.
     *
     * @param formatMajor header major_version (2)
     * @param formatMinor header minor_version (1 or 3)
     * @param thisPackage the package described by the file
     * @param referenced  referenced packages (written only for format 2.3)
     * @param classes     class_info entries
     * @return the encoded export file
     */
    public static byte[] write(int formatMajor, int formatMinor, Pkg thisPackage,
                               List<Pkg> referenced, List<Cls> classes) {
        return new ExpFixture().encode(formatMajor, formatMinor, thisPackage, referenced, classes);
    }

    /**
     * Writes an export file (format 2.1) with the content of an in-memory model, e.g. a
     * built-in API package of a given version.
     *
     * @param ef the model; class names must be fully qualified
     * @return the encoded export file
     */
    public static byte[] write(ExportFile ef) {
        Pkg pkg = new Pkg(ef.packageName(), ef.packageFlags(), ef.majorVersion(), ef.minorVersion(),
                HexFormat.of().formatHex(ef.aid()));
        List<Cls> classes = new ArrayList<>();
        for (ExportFile.ClassExport c : ef.classes()) {
            List<Field> fields = c.fields().stream()
                    .map(f -> new Field(f.token(), f.accessFlags(), f.name(), f.descriptor(), f.constantValue()))
                    .toList();
            List<Method> methods = c.methods().stream()
                    .map(m -> new Method(m.token(), m.accessFlags(), m.name(), m.descriptor()))
                    .toList();
            classes.add(new Cls(c.token(), c.accessFlags(), c.name(), c.supers(), c.interfaces(),
                    fields, methods, 0));
        }
        return write(2, 1, pkg, List.of(), classes);
    }

    private byte[] encode(int formatMajor, int formatMinor, Pkg thisPackage,
                          List<Pkg> referenced, List<Cls> classes) {
        boolean v23 = formatMinor >= 3;
        int thisIdx = pkg(thisPackage);
        List<Integer> refIdx = referenced.stream().map(this::pkg).toList();
        var body = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(body)) {
            out.writeShort(thisIdx);
            if (v23) {
                out.writeByte(refIdx.size());
                for (int r : refIdx) out.writeShort(r);
            }
            out.writeByte(classes.size());
            for (Cls c : classes) writeClass(out, c, v23);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var file = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(file)) {
            out.writeInt(0x00FACADE);
            out.writeByte(formatMinor);
            out.writeByte(formatMajor);
            out.writeShort(cp.size());
            for (Object e : cp) writeCp(out, e);
            out.write(body.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file.toByteArray();
    }

    private void writeClass(DataOutputStream out, Cls c, boolean v23) throws IOException {
        out.writeByte(c.token());
        out.writeShort(c.flags());
        out.writeShort(classref(c.name()));
        out.writeShort(c.supers().size());
        for (String s : c.supers()) out.writeShort(classref(s));
        out.writeByte(c.interfaces().size());
        for (String i : c.interfaces()) out.writeShort(classref(i));
        out.writeShort(c.fields().size());
        for (Field f : c.fields()) {
            out.writeByte(f.token());
            out.writeShort(f.flags());
            out.writeShort(utf8(f.name()));
            out.writeShort(utf8(f.descriptor()));
            if (f.constant() == null) {
                out.writeShort(0);
            } else {
                out.writeShort(1);
                out.writeShort(utf8("ConstantValue"));
                out.writeInt(2);
                out.writeShort(integer(f.constant()));
            }
        }
        out.writeShort(c.methods().size());
        for (Method m : c.methods()) {
            out.writeByte(m.token());
            out.writeShort(m.flags());
            out.writeShort(utf8(m.name()));
            out.writeShort(utf8(m.descriptor()));
        }
        if (v23) out.writeByte(c.cap22InheritableCount());
    }

    private static void writeCp(DataOutputStream out, Object e) throws IOException {
        switch (e) {
            case String s -> {
                byte[] b = s.getBytes(StandardCharsets.UTF_8);
                out.writeByte(1);
                out.writeShort(b.length);
                out.write(b);
            }
            case IntConst i -> {
                out.writeByte(3);
                out.writeInt(i.value());
            }
            case ClassRef r -> {
                out.writeByte(7);
                out.writeShort(r.nameIndex());
            }
            case PkgRef p -> {
                byte[] aid = HexFormat.of().parseHex(p.pkg().aidHex());
                out.writeByte(13);
                out.writeByte(p.pkg().flags());
                out.writeShort(p.nameIndex());
                out.writeByte(p.pkg().minor());
                out.writeByte(p.pkg().major());
                out.writeByte(aid.length);
                out.write(aid);
            }
            default -> throw new IllegalStateException("unknown cp entry " + e);
        }
    }

    private int utf8(String s) {
        return indexOf(s);
    }

    private int integer(int value) {
        return indexOf(new IntConst(value));
    }

    private int classref(String name) {
        return indexOf(new ClassRef(utf8(name)));
    }

    private int pkg(Pkg p) {
        return indexOf(new PkgRef(utf8(p.name()), p));
    }

    private int indexOf(Object entry) {
        for (int i = 0; i < cp.size(); i++) {
            if (Objects.equals(cp.get(i), entry)) return i;
        }
        cp.add(entry);
        return cp.size() - 1;
    }

    private record IntConst(int value) {}

    private record ClassRef(int nameIndex) {}

    private record PkgRef(int nameIndex, Pkg pkg) {}
}
