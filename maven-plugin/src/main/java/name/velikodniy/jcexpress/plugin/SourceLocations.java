package name.velikodniy.jcexpress.plugin;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Maps what the converter reports back to the sources: a bytecode offset of a method to its
 * source line (LineNumberTable, JVMS &sect;4.7.12), and a class name to the instructions that
 * use it. Source files are looked up in the compile source roots, so messages can name
 * {@code /path/to/WalletApplet.java:[42]} like the compiler does.
 */
final class SourceLocations {

    private final ClassIndex index;
    private final List<Path> sourceRoots;

    /**
     * @param index       the project classes
     * @param sourceRoots the compile source roots of the project
     */
    SourceLocations(ClassIndex index, List<Path> sourceRoots) {
        this.index = index;
        this.sourceRoots = List.copyOf(sourceRoots);
    }

    /**
     * A place in the sources.
     *
     * @param type   the class
     * @param method method name and descriptor, e.g. {@code process(Ljavacard/framework/APDU;)V},
     *               or empty if the location is the class itself
     * @param line   source line, or -1 if the class file has no line numbers
     */
    record Location(ClassSummary type, String method, int line) {
    }

    /**
     * Finds the source line of a bytecode offset.
     *
     * @param className internal class name
     * @param method    method name followed by its descriptor, or empty for the class itself
     * @param bci       bytecode offset, negative if the problem is not in the code
     * @return the location, if the class is a project class
     */
    Optional<Location> at(String className, String method, int bci) {
        Optional<ClassSummary> type = index.projectClass(className);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        int line = bci < 0 ? -1 : model(type.get()).flatMap(m -> code(m, method))
                .map(code -> lineAt(code, bci)).orElse(-1);
        return Optional.of(new Location(type.get(), method, line));
    }

    /**
     * Finds where a local variable is declared: the line of the instruction that stores its first
     * value, which is the instruction before the start of its scope in the LocalVariableTable
     * (JVMS &sect;4.7.13); for a parameter, whose scope starts at the first instruction, the first line
     * of the method.
     *
     * @param className internal class name
     * @param method    method name followed by its descriptor
     * @param variable  the name of the local variable
     * @return the location, if the class is a project class compiled with local variable names
     */
    Optional<Location> declaration(String className, String method, String variable) {
        Optional<ClassSummary> type = index.projectClass(className);
        Optional<CodeModel> code = type.flatMap(this::model).flatMap(m -> code(m, method));
        if (code.isEmpty() || !(code.get() instanceof CodeAttribute attribute)) {
            return Optional.empty();
        }
        return code.get().elementStream()
                .filter(e -> e instanceof LocalVariable lv && lv.name().equalsString(variable))
                .map(e -> attribute.labelToBci(((LocalVariable) e).startScope()))
                .findFirst()
                .map(start -> new Location(type.get(), method, lineBefore(code.get(), start)));
    }

    /**
     * Finds the instructions of a package that use a class (field and method references, object
     * and array creation, casts, instanceof, class literals).
     *
     * @param packageName the package whose classes are searched (dot notation)
     * @param target      matches internal class names
     * @param limit       maximum number of locations
     * @return the locations, in class and code order
     */
    List<Location> usages(String packageName, Predicate<String> target, int limit) {
        List<Location> result = new ArrayList<>();
        for (ClassSummary type : index.classesOf(packageName)) {
            model(type).ifPresent(model -> model.methods().forEach(method -> method.code()
                    .ifPresent(code -> collect(type, method, code, target, result))));
        }
        return result.size() > limit ? List.copyOf(result.subList(0, limit)) : List.copyOf(result);
    }

    /**
     * Finds the classes that the instructions of a package use (the same instructions as
     * {@link #usages}).
     *
     * @param packageName the package whose classes are searched (dot notation)
     * @param target      matches internal class names
     * @return the matching internal class names, sorted
     */
    Set<String> referencedClasses(String packageName, Predicate<String> target) {
        Set<String> classes = new TreeSet<>();
        usages(packageName, name -> {
            boolean used = target.test(name);
            if (used) {
                classes.add(name);
            }
            return used;
        }, Integer.MAX_VALUE);
        return classes;
    }

    /**
     * Formats a location like the Java compiler: {@code <source file>:[<line>] <class>.<method>}.
     *
     * @param location the location
     * @return the text
     */
    String format(Location location) {
        String file = sourcePath(location.type());
        String where = location.line() < 0 ? file : file + ":[" + location.line() + "]";
        String method = location.method().isEmpty() ? "" : "." + methodName(location.method());
        return where + " " + location.type().javaName() + method;
    }

    private void collect(ClassSummary type, MethodModel method, CodeModel code, Predicate<String> target,
                         List<Location> result) {
        int line = -1;
        String signature = method.methodName().stringValue() + method.methodType().stringValue();
        for (CodeElement element : code) {
            if (element instanceof LineNumber ln) {
                line = ln.line();
            } else if (element instanceof Instruction instruction && references(instruction, target)) {
                Location location = new Location(type, signature, line);
                if (!result.contains(location)) {
                    result.add(location);
                }
            }
        }
    }

    private static boolean references(Instruction instruction, Predicate<String> target) {
        return switch (instruction) {
            case FieldInstruction f -> target.test(f.owner().asInternalName());
            case InvokeInstruction i -> target.test(i.owner().asInternalName());
            case NewObjectInstruction n -> target.test(n.className().asInternalName());
            case NewReferenceArrayInstruction n -> matches(n.componentType(), target);
            case NewMultiArrayInstruction n -> matches(n.arrayType(), target);
            case TypeCheckInstruction t -> matches(t.type(), target);
            case ConstantInstruction c -> c.constantValue() instanceof ClassDesc d && matchesDesc(d, target);
            default -> false;
        };
    }

    private static boolean matches(ClassEntry entry, Predicate<String> target) {
        return matchesDesc(entry.asSymbol(), target);
    }

    private static boolean matchesDesc(ClassDesc type, Predicate<String> target) {
        ClassDesc element = type;
        while (element.isArray()) {
            element = element.componentType();
        }
        return element.isClassOrInterface() && target.test(internalName(element));
    }

    private static String internalName(ClassDesc type) {
        String descriptor = type.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1);
    }

    /** The line of the last instruction before an offset, or of the first instruction for offset 0. */
    private static int lineBefore(CodeModel code, int bci) {
        int line = -1;
        int lineOfPrevious = -1;
        int offset = 0;
        for (CodeElement element : code) {
            if (element instanceof LineNumber ln) {
                line = ln.line();
            } else if (element instanceof Instruction instruction) {
                if (offset >= bci) {
                    return lineOfPrevious >= 0 ? lineOfPrevious : line;
                }
                lineOfPrevious = line;
                offset += instruction.sizeInBytes();
            }
        }
        return lineOfPrevious;
    }

    private static int lineAt(CodeModel code, int bci) {
        int line = -1;
        int offset = 0;
        for (CodeElement element : code) {
            if (element instanceof LineNumber ln) {
                line = ln.line();
            } else if (element instanceof Instruction instruction) {
                if (offset >= bci) {
                    return offset == bci ? line : -1;
                }
                offset += instruction.sizeInBytes();
            }
        }
        return -1;
    }

    private Optional<ClassModel> model(ClassSummary type) {
        return index.classFile(type).map(bytes -> ClassFile.of().parse(bytes));
    }

    private static Optional<CodeModel> code(ClassModel model, String method) {
        return model.methods().stream()
                .filter(m -> method.equals(m.methodName().stringValue() + m.methodType().stringValue()))
                .findFirst().flatMap(MethodModel::code);
    }

    private String sourcePath(ClassSummary type) {
        if (type.sourceFile() == null) {
            return type.name() + ".class";
        }
        String pkgDir = type.packageName().replace('.', '/');
        String relative = pkgDir.isEmpty() ? type.sourceFile() : pkgDir + "/" + type.sourceFile();
        return sourceRoots.stream().map(root -> root.resolve(relative)).filter(Files::isRegularFile)
                .findFirst().map(Path::toString).orElse(relative);
    }

    private static String methodName(String method) {
        int paren = method.indexOf('(');
        return paren < 0 ? method : method.substring(0, paren) + "()";
    }
}
