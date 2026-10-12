package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.input.CodeLines;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the symbolic references of a package's class files, with the place where each
 * occurs: superclasses and interfaces, types used in field and method descriptors, classes
 * named by instructions and exception handlers, and field and method references.
 *
 * <p>The converter uses them before translation to find the export files of imported packages
 * (JCVM 3.1 §4.3.3), to check that every referenced API element exists on the target platform
 * (§4.3.5, §4.5.2) and to import every package whose classes appear in class or descriptor
 * structures (§6.7, §6.9, §6.14), with error messages that name class, method and source line.
 */
public final class ClassReferences {

    /** What a reference denotes; method kinds follow the invoke instruction. */
    public enum Kind {
        /** Class named by an instruction ({@code new}, {@code checkcast}, ...) or a catch clause. */
        CLASS,
        /** Direct superclass of a class. */
        SUPERCLASS,
        /** Interface implemented or extended by a class or interface. */
        INTERFACE,
        /** Class used in a field or method descriptor. */
        TYPE,
        /** Static field access. */
        STATIC_FIELD,
        /** Instance field access. */
        INSTANCE_FIELD,
        /** {@code invokestatic}. */
        STATIC_METHOD,
        /** {@code invokevirtual}. */
        VIRTUAL_METHOD,
        /** {@code invokespecial} (constructors, super calls, private methods). */
        SPECIAL_METHOD,
        /** {@code invokeinterface}. */
        INTERFACE_METHOD;

        /** Returns {@code true} for field and method references. */
        public boolean isMember() {
            return ordinal() >= STATIC_FIELD.ordinal();
        }
    }

    /**
     * Where in a class file a reference occurs, in the notation of the subset check's violations
     * ({@link name.velikodniy.jcexpress.converter.check.Violation#context()}).
     *
     * @param className internal name of the class whose class file holds the reference
     * @param member    {@code superclass}, {@code interface <name>}, {@code field <name>}, or a method as
     *                  {@code <name><descriptor>}
     * @param line      the source line of the instruction (of a catch clause: of its handler), or -1 for a reference
     *                  in a declaration or in code without a LineNumberTable
     * @param code      whether the reference is in the code of the member (an instruction or a catch clause) rather
     *                  than in its declaration (a superclass, an interface, the type of a field, a method signature);
     *                  code of a class file without a LineNumberTable (JVMS §4.7.12) has no line but is still code
     */
    public record Site(String className, String member, int line, boolean code) {
    }

    /**
     * One symbolic reference.
     *
     * @param kind       what is referenced
     * @param owner      internal name of the referenced class, or of the member's owner
     * @param name       member name ({@code null} for class references)
     * @param descriptor member descriptor ({@code null} for class references)
     * @param location   where the reference occurs, for diagnostics
     * @param site       where the reference occurs, structured; {@code null} for a reference built in code without
     *                   a class file
     */
    public record Reference(Kind kind, String owner, String name, String descriptor, String location, Site site) {

        /**
         * Creates a reference without a structured site.
         *
         * @param kind       what is referenced
         * @param owner      internal name of the referenced class, or of the member's owner
         * @param name       member name ({@code null} for class references)
         * @param descriptor member descriptor ({@code null} for class references)
         * @param location   where the reference occurs, for diagnostics
         */
        public Reference(Kind kind, String owner, String name, String descriptor, String location) {
            this(kind, owner, name, descriptor, location, null);
        }

        /** Returns the package of {@link #owner()} in internal form. */
        public String ownerPackage() {
            int slash = owner.lastIndexOf('/');
            return slash < 0 ? "" : owner.substring(0, slash);
        }

        /** Returns {@code owner.name descriptor} or the class name, in Java notation. */
        public String target() {
            String cls = owner.replace('/', '.');
            return name == null ? cls : cls + "." + name + descriptor;
        }
    }

    private final List<Reference> references = new ArrayList<>();
    private String className;
    private String sourceFile;

    private ClassReferences() {}

    /**
     * Scans class files.
     *
     * @param classFiles the bytes of every class file of the package
     * @return the references in class-file order, without duplicates
     */
    public static List<Reference> scan(List<byte[]> classFiles) {
        ClassReferences scanner = new ClassReferences();
        for (byte[] bytes : classFiles) {
            scanner.scanClass(ClassFile.of().parse(bytes));
        }
        return List.copyOf(new LinkedHashSet<>(scanner.references));
    }

    /**
     * Returns the packages (internal names) of all referenced classes, excluding one package.
     *
     * @param refs           references from {@link #scan}
     * @param currentPackage the package being converted (internal form)
     * @return referenced package names in first-reference order
     */
    public static Set<String> packages(List<Reference> refs, String currentPackage) {
        Set<String> result = new LinkedHashSet<>();
        for (Reference r : refs) {
            if (!r.ownerPackage().equals(currentPackage)) result.add(r.ownerPackage());
        }
        return result;
    }

    private void scanClass(ClassModel cm) {
        className = cm.thisClass().asInternalName();
        sourceFile = cm.findAttribute(Attributes.sourceFile())
                .map(sf -> sf.sourceFile().stringValue()).orElse(null);
        String where = className.replace('/', '.');
        cm.superclass().ifPresent(s -> addClass(Kind.SUPERCLASS, s.asInternalName(), where,
                new Site(className, "superclass", -1, false)));
        for (ClassEntry i : cm.interfaces()) {
            addClass(Kind.INTERFACE, i.asInternalName(), where,
                    new Site(className, "interface " + i.asInternalName(), -1, false));
        }
        for (FieldModel f : cm.fields()) {
            String field = f.fieldName().stringValue();
            addType(f.fieldTypeSymbol(), where + "." + field, new Site(className, "field " + field, -1, false));
        }
        for (MethodModel m : cm.methods()) {
            String member = m.methodName().stringValue() + m.methodType().stringValue();
            String method = where + "." + member;
            addTypes(m.methodTypeSymbol(), method, new Site(className, member, -1, false));
            m.code().ifPresent(code -> scanCode(code, method, member));
        }
    }

    private void scanCode(CodeModel code, String method, String member) {
        Map<Label, Integer> labelLines = CodeLines.atLabels(code);
        int line = -1;
        for (CodeElement e : code) {
            if (e instanceof LineNumber ln) {
                line = ln.line();
            } else if (e instanceof ExceptionCatch ec) {
                // the conversion's location names the method; the site has the line of the catch clause (JVMS §4.7.3)
                Site handler = new Site(className, member, labelLines.getOrDefault(ec.handler(), -1), true);
                String where = at(method, line);
                ec.catchType().ifPresent(t -> addClass(Kind.CLASS, t.asInternalName(), where, handler));
            } else {
                scanElement(e, at(method, line), new Site(className, member, line, true));
            }
        }
    }

    private void scanElement(CodeElement e, String where, Site site) {
        switch (e) {
            case InvokeInstruction ii -> references.add(new Reference(invokeKind(ii.opcode()),
                    ii.owner().asInternalName(), ii.name().stringValue(), ii.type().stringValue(), where, site));
            case FieldInstruction fi -> references.add(new Reference(
                    fi.opcode() == Opcode.GETSTATIC || fi.opcode() == Opcode.PUTSTATIC
                            ? Kind.STATIC_FIELD : Kind.INSTANCE_FIELD,
                    fi.owner().asInternalName(), fi.name().stringValue(), fi.type().stringValue(), where, site));
            case NewObjectInstruction no -> addClass(Kind.CLASS, no.className().asInternalName(), where, site);
            case TypeCheckInstruction tc -> addClass(Kind.CLASS, tc.type().asInternalName(), where, site);
            case NewReferenceArrayInstruction na ->
                    addClass(Kind.CLASS, na.componentType().asInternalName(), where, site);
            default -> { /* other instructions name no classes */ }
        }
    }

    private static Kind invokeKind(Opcode op) {
        return switch (op) {
            case INVOKESTATIC -> Kind.STATIC_METHOD;
            case INVOKESPECIAL -> Kind.SPECIAL_METHOD;
            case INVOKEINTERFACE -> Kind.INTERFACE_METHOD;
            default -> Kind.VIRTUAL_METHOD;
        };
    }

    private String at(String method, int line) {
        if (line < 0) return method;
        return method + " (" + (sourceFile != null ? sourceFile : className) + ":" + line + ")";
    }

    private void addTypes(MethodTypeDesc type, String where, Site site) {
        addType(type.returnType(), where, site);
        for (ClassDesc p : type.parameterList()) addType(p, where, site);
    }

    private void addType(ClassDesc type, String where, Site site) {
        ClassDesc t = type;
        while (t.isArray()) t = t.componentType();
        if (t.isClassOrInterface()) {
            String desc = t.descriptorString();
            references.add(new Reference(Kind.TYPE, desc.substring(1, desc.length() - 1), null, null, where, site));
        }
    }

    /** Adds a class reference; array class names are reduced to their element class. */
    private void addClass(Kind kind, String internalName, String where, Site site) {
        String name = internalName;
        if (name.startsWith("[")) {
            int l = name.indexOf('L');
            if (l < 0) return; // array of a primitive type
            name = name.substring(l + 1, name.length() - 1);
        }
        references.add(new Reference(kind, name, null, null, where, site));
    }
}
