package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.check.Violation;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inlines the {@code jsr}/{@code ret} subroutines of the class files of a package before they are checked
 * and translated.
 *
 * <p>JCVM 3.1 §2.3.2.2 lists {@code jsr} and {@code ret} among the supported class file bytecodes (with the
 * JCVM instructions of §7.5.69 and §7.5.79). Compilers used them for {@code finally} blocks in class files of
 * version 49 and older: javac 1.3 and ECJ with {@code -target} 1.4 or lower (JVMS 4.10.2.5). The converter
 * replaces every {@code jsr} by a copy of the subroutine it calls, placed at the call as javac and ECJ place
 * {@code finally} code for later targets ({@link SubroutineCopies}), so the rest of the converter and the CAP
 * file never deal with return addresses; {@code jsr_w} and a {@code wide ret} (JCVM 3.1 §2.3.2.1, §2.3.2.3.4:
 * not in the subset) disappear the same way, as {@code goto_w} is translated. Shapes that copies could not
 * reproduce exactly ({@link Subroutines}) are reported as violations; so are subroutines in class files of
 * version 51.0 and later, which JVMS 4.9.1 forbids.
 *
 * <p>Only methods with subroutines are rewritten; the rest of the class file is kept. Later messages about a
 * rewritten method give bytecode indexes of the inlined code and the original source lines.
 */
final class SubroutineInliner {

    private static final int JAVA_7 = 51;
    private static final String CANNOT_INLINE = "jsr/ret subroutine that cannot be inlined: ";
    private static final String SPEC = " (JCVM 3.1 §2.3.2.2, §7.5.69, §7.5.79)";
    private static final String REMEDY = "; compile with javac -target 1.6 or later or ECJ -target 1.5 or later,"
            + " which inline finally blocks";
    private static final String TOO_NEW = "jsr, jsr_w and ret must not appear in class files of version 51.0 and"
            + " later (JVMS 4.9.1): the class file is invalid; compile it again";
    private static final String TOO_LARGE = "inlining the subroutines gives more than the 65535 bytes of code a"
            + " method can hold (JVMS 4.7.3), and JCVM 3.1 §2.2.4.4 allows at most 32767 bytes of bytecode per method"
            + " (the class file writer reports: %s); split the method into smaller ones";

    private SubroutineInliner() {}

    /**
     * Inlines the subroutines of the class files of a package.
     *
     * @param classFiles class file bytes by internal class name
     * @return {@code classFiles} itself if no class has subroutines, otherwise a new map in the same order
     * @throws ConverterException listing every method whose subroutines cannot be inlined
     */
    static Map<String, byte[]> inline(Map<String, byte[]> classFiles) throws ConverterException {
        List<Violation> violations = new ArrayList<>();
        Map<String, byte[]> inlined = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, byte[]> e : classFiles.entrySet()) {
            byte[] bytes = inline(e.getValue(), violations);
            changed |= bytes != e.getValue();
            inlined.put(e.getKey(), bytes);
        }
        if (!violations.isEmpty()) {
            throw new ConverterException("jsr/ret subroutines that cannot be inlined", violations);
        }
        return changed ? inlined : classFiles;
    }

    /**
     * Inlines the subroutines of one class file.
     *
     * @param classFile  the class file
     * @param violations receives one violation per method whose subroutines cannot be inlined
     * @return {@code classFile} itself if it has no subroutines or a violation was found, otherwise the
     *         class file with the subroutines inlined
     */
    static byte[] inline(byte[] classFile, List<Violation> violations) {
        ClassModel model = ClassFile.of().parse(classFile);
        Map<String, InlinedMethod> methods = new HashMap<>();
        int before = violations.size();
        for (MethodModel method : model.methods()) {
            Optional<CodeAttribute> code = method.findAttribute(Attributes.code());
            if (code.isPresent() && hasSubroutines(code.get())) {
                analyze(model, method, new IndexedCode(code.get()), methods, violations);
            }
        }
        if (methods.isEmpty() || violations.size() > before) {
            return classFile;
        }
        try {
            return transform(model, methods);
        } catch (IllegalArgumentException e) {
            tooLarge(model, methods, violations);
            if (violations.size() == before) {
                throw e;
            }
            return classFile;
        }
    }

    private static byte[] transform(ClassModel model, Map<String, InlinedMethod> methods) {
        return ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).transformClass(model,
                (cb, element) -> rewrite(cb, element, methods));
    }

    /**
     * Finds the methods whose inlined code a class file cannot hold (the class file writer refuses more than 65535
     * bytes of code, JVMS 4.7.3), one rewrite each, and reports them as violations.
     */
    private static void tooLarge(ClassModel model, Map<String, InlinedMethod> methods, List<Violation> violations) {
        for (Map.Entry<String, InlinedMethod> entry : methods.entrySet()) {
            try {
                transform(model, Map.of(entry.getKey(), entry.getValue()));
            } catch (IllegalArgumentException e) {
                InlinedMethod method = entry.getValue();
                violations.add(violation(model, method.method(), method.code(), firstSubroutineInstruction(method),
                        CANNOT_INLINE + String.format(TOO_LARGE, e.getMessage()) + SPEC));
            }
        }
    }

    private static int firstSubroutineInstruction(InlinedMethod method) {
        int first = 0;
        while (!(method.code().at(first) instanceof DiscontinuedInstruction)) {
            first++;
        }
        return first;
    }

    private record InlinedMethod(MethodModel method, IndexedCode code, Subroutines subroutines) {}

    private static boolean hasSubroutines(CodeAttribute code) {
        return code.elementStream().anyMatch(e -> e instanceof DiscontinuedInstruction);
    }

    private static void analyze(ClassModel model, MethodModel method, IndexedCode code,
                                Map<String, InlinedMethod> methods, List<Violation> violations) {
        if (model.majorVersion() >= JAVA_7) {
            int first = 0;
            while (!(code.at(first) instanceof DiscontinuedInstruction)) {
                first++;
            }
            violations.add(violation(model, method, code, first, TOO_NEW));
            return;
        }
        try {
            methods.put(key(method), new InlinedMethod(method, code, new Subroutines(code)));
        } catch (Subroutines.Unsupported e) {
            violations.add(violation(model, method, code, e.index(), CANNOT_INLINE + e.getMessage() + SPEC + REMEDY));
        }
    }

    private static void rewrite(ClassBuilder cb, ClassElement element, Map<String, InlinedMethod> methods) {
        InlinedMethod inlined = element instanceof MethodModel m ? methods.get(key(m)) : null;
        if (inlined == null) {
            cb.with(element);
            return;
        }
        cb.transformMethod((MethodModel) element, (mb, me) -> {
            if (me instanceof CodeModel) {
                mb.withCode(out -> SubroutineCopies.write(inlined.code(), inlined.subroutines(), out));
            } else {
                mb.with(me);
            }
        });
    }

    private static String key(MethodModel method) {
        return method.methodName().stringValue() + method.methodType().stringValue();
    }

    private static Violation violation(ClassModel model, MethodModel method, IndexedCode code, int index,
                                       String message) {
        String sourceFile = model.findAttribute(Attributes.sourceFile())
                .map(sf -> sf.sourceFile().stringValue()).orElse(null);
        return new Violation(model.thisClass().asInternalName(), key(method), code.bci(index), message,
                sourceFile, code.line(index));
    }
}
