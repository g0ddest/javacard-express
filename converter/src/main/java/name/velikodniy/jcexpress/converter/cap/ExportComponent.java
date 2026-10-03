package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.token.ImportedTypes;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates the CAP Export component (tag 10) as defined in JCVM 3.1 §6.13.
 *
 * <p>The component lists the elements other packages may link to, indexed by class token:
 * <ul>
 *   <li>a <b>library package</b> (no applets) exports every public class and interface, with the
 *       offsets of each public class's public and protected static fields, static methods and
 *       constructors (compile-time constants excluded);</li>
 *   <li>an <b>application package</b> exports only its public shareable interfaces (interfaces
 *       that are or extend {@code javacard.framework.Shareable}), without members.</li>
 * </ul>
 * An index into {@code class_exports} equals the class token (§6.13), and {@code class_count}
 * must be greater than zero: when nothing is exported the component is omitted.
 *
 * <pre>
 * u1  tag = 10
 * u2  size
 * u1  class_count
 * class_export_info { u2 class_offset; u1 static_field_count; u1 static_method_count;
 *                     u2 static_field_offsets[]; u2 static_method_offsets[] } class_exports[]
 * </pre>
 *
 * @see ClassComponent
 * @see StaticFieldComponent
 * @see MethodComponent
 */
public final class ExportComponent {

    public static final int TAG = 10;

    private ExportComponent() {}

    /**
     * What the Export component describes.
     *
     * @param classes            classes and interfaces of the package
     * @param tokenMap           token assignment
     * @param classOffsets       internal class name to Class component offset
     * @param methodOffsets      {@code "class:name:descriptor"} to Method component offset
     * @param staticFieldOffsets {@code "class:field"} to static field image offset
     * @param hasApplets         {@code true} for an application package (§6.13)
     * @param imported           information about imported interfaces (to recognize shareable ones)
     */
    public record Input(List<ClassInfo> classes, TokenMap tokenMap, Map<String, Integer> classOffsets,
                        Map<String, Integer> methodOffsets, Map<String, Integer> staticFieldOffsets,
                        boolean hasApplets, ImportedTypes imported) {}

    /**
     * Generates the Export component (§6.13).
     *
     * @param in the package
     * @return complete component bytes including tag and size, or empty if the package exports
     *         nothing (the component must then be omitted)
     * @throws IllegalStateException if the exported classes do not have the tokens 0..n-1 or an
     *                               exported member has no location
     */
    public static Optional<byte[]> generate(Input in) {
        TypeHierarchy hierarchy = new TypeHierarchy(in.classes(), in.tokenMap(), in.imported());
        List<ClassInfo> exported = in.classes().stream()
                .filter(ci -> (ci.accessFlags() & 0x0001) != 0)
                .filter(ci -> !in.hasApplets() || (ci.isInterface() && hierarchy.isShareableInterface(ci.thisClass())))
                .sorted(Comparator.comparingInt(ci -> in.tokenMap().classToken(ci.thisClass())))
                .toList();
        if (exported.isEmpty()) {
            return Optional.empty();
        }
        var info = new BinaryWriter();
        info.u1(exported.size()); // class_count
        for (int i = 0; i < exported.size(); i++) {
            writeClassExport(info, in, exported.get(i), i);
        }
        return Optional.of(HeaderComponent.wrapComponent(TAG, info.toByteArray()));
    }

    /** class_export_info; interfaces have no static members (§6.13). */
    private static void writeClassExport(BinaryWriter info, Input in, ClassInfo ci, int index) {
        TokenMap.ClassEntry entry = in.tokenMap().findClass(ci.thisClass());
        requireTokenIndex(ci.thisClass(), entry.token(), index);
        List<TokenMap.FieldEntry> fields = ci.isInterface() ? List.of() : sortedFields(entry.staticFields());
        List<TokenMap.MethodEntry> methods = ci.isInterface() ? List.of() : sortedMethods(entry.staticMethods());
        info.u2(required(in.classOffsets(), ci.thisClass()));               // class_offset
        info.u1(fields.size());                                            // static_field_count
        info.u1(methods.size());                                           // static_method_count
        for (TokenMap.FieldEntry fe : fields) {                            // static_field_offsets[token]
            info.u2(required(in.staticFieldOffsets(), ci.thisClass() + ":" + fe.name()));
        }
        for (TokenMap.MethodEntry me : methods) {                          // static_method_offsets[token]
            info.u2(required(in.methodOffsets(), ci.thisClass() + ":" + me.name() + ":" + me.descriptor()));
        }
    }

    /** An index into {@code class_exports} is the class token (§6.13). */
    private static void requireTokenIndex(String className, int token, int index) {
        if (token != index) {
            throw new IllegalStateException("Exported " + className + " has class token " + token
                    + " but is entry " + index + " of the Export component (JCVM 3.1 §6.13)");
        }
    }

    private static List<TokenMap.FieldEntry> sortedFields(List<TokenMap.FieldEntry> fields) {
        List<TokenMap.FieldEntry> sorted = new ArrayList<>(fields);
        sorted.sort(Comparator.comparingInt(TokenMap.FieldEntry::token));
        return sorted;
    }

    private static List<TokenMap.MethodEntry> sortedMethods(List<TokenMap.MethodEntry> methods) {
        List<TokenMap.MethodEntry> sorted = new ArrayList<>(methods);
        sorted.sort(Comparator.comparingInt(TokenMap.MethodEntry::token));
        return sorted;
    }

    private static int required(Map<String, Integer> offsets, String key) {
        Integer offset = offsets.get(key);
        if (offset == null) {
            throw new IllegalStateException("Exported element " + key + " has no location in the CAP file"
                    + " (JCVM 3.1 §6.13)");
        }
        return offset;
    }

    /**
     * Generates an Export component that lists every class of the token map that has a class
     * token, as for a library package.
     *
     * @param tokenMap             token assignment
     * @param methodOffsets        Method component offset per global method index
     * @param classOffsets         Class component offset per position in {@link TokenMap#classes()}
     * @param methodIndexMap       {@code "class:name:descriptor"} to global method index
     * @param staticFieldOffsetMap {@code "class:field"} to static field image offset
     * @return complete component bytes including tag and size
     * @throws IllegalStateException if the class tokens are not 0..n-1 or the location of an exported
     *                               class, static field or static method is not given (earlier releases wrote
     *                               such an element with offset 0, a real location of another element)
     * @deprecated cannot tell application from library packages or recognize shareable
     *             interfaces; use {@link #generate(Input)}
     */
    @Deprecated
    public static byte[] generate(TokenMap tokenMap, int[] methodOffsets, int[] classOffsets,
                                  Map<String, Integer> methodIndexMap, Map<String, Integer> staticFieldOffsetMap) {
        Map<String, Integer> classes = new HashMap<>();
        for (int i = 0; i < tokenMap.classes().size() && i < classOffsets.length; i++) {
            classes.put(tokenMap.classes().get(i).internalName(), classOffsets[i]);
        }
        Map<String, Integer> methods = new HashMap<>();
        methodIndexMap.forEach((key, idx) -> {
            if (idx >= 0 && idx < methodOffsets.length) {
                methods.put(key, methodOffsets[idx]);
            }
        });
        List<TokenMap.ClassEntry> exported = tokenMap.classes().stream()
                .filter(ce -> ce.token() != TokenMap.NO_TOKEN)
                .sorted(Comparator.comparingInt(TokenMap.ClassEntry::token)).toList();
        var info = new BinaryWriter();
        info.u1(exported.size());
        for (int i = 0; i < exported.size(); i++) {
            writeClassExport(info, exported.get(i), i, classes, methods, staticFieldOffsetMap);
        }
        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }

    /** class_export_info of the deprecated variant: every location must be given (§6.13). */
    private static void writeClassExport(BinaryWriter info, TokenMap.ClassEntry ce, int index,
                                         Map<String, Integer> classes, Map<String, Integer> methods,
                                         Map<String, Integer> fields) {
        requireTokenIndex(ce.internalName(), ce.token(), index);
        info.u2(required(classes, ce.internalName()));
        info.u1(ce.staticFields().size());
        info.u1(ce.staticMethods().size());
        for (TokenMap.FieldEntry fe : sortedFields(ce.staticFields())) {
            info.u2(required(fields, ce.internalName() + ":" + fe.name()));
        }
        for (TokenMap.MethodEntry me : sortedMethods(ce.staticMethods())) {
            info.u2(required(methods, ce.internalName() + ":" + me.name() + ":" + me.descriptor()));
        }
    }
}
