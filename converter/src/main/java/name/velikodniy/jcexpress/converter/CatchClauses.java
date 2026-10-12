package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.check.ForbiddenTypes;
import name.velikodniy.jcexpress.converter.check.SubsetChecker;
import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.input.CodeLines;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ExceptionCatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The lines of the catch clauses the subset check reports. A catch clause that names a class outside the Java Card
 * platform (JCVM 3.1 §2.2.1.4) is a violation of the method's declaration, without a line, because the clause has no
 * instruction; the class file's exception table and LineNumberTable have the line of the clause's handler
 * (JVMS §4.7.3, §4.7.12), which is the line of the clause.
 */
final class CatchClauses {

    private final Map<String, byte[]> classFiles;
    /** How many violations of each catch clause message of a method were placed so far, to take the next clause. */
    private final Map<String, Integer> placed = new HashMap<>();

    /**
     * @param classFiles the class files of the package by internal name
     */
    CatchClauses(Map<String, byte[]> classFiles) {
        this.classFiles = classFiles;
    }

    /**
     * Returns the problem of a violation: a catch clause's at the line of its handler, every other as it is.
     *
     * @param violation a violation of the subset check
     * @return the problem
     */
    PackageCheck.Problem place(Violation violation) {
        PackageCheck.Problem problem = PackageCheck.Problem.of(violation);
        if (violation.bci() >= 0 || !violation.message().startsWith(SubsetChecker.CATCH_CLAUSE)) {
            return problem;
        }
        int index = placed.merge(violation.className() + " " + violation.context() + " " + violation.message(), 1,
                Integer::sum) - 1;
        return handlerLine(violation, index)
                .map(line -> new PackageCheck.Problem(problem.className(), problem.member(), line, false,
                        problem.message()))
                .orElse(problem);
    }

    /** The line of the {@code index}-th handler of the violation's method whose catch type gives its message. */
    private Optional<Integer> handlerLine(Violation violation, int index) {
        byte[] bytes = classFiles.get(violation.className());
        if (bytes == null) {
            return Optional.empty();
        }
        String reason = violation.message().substring(SubsetChecker.CATCH_CLAUSE.length());
        for (MethodModel method : ClassFile.of().parse(bytes).methods()) {
            if ((method.methodName().stringValue() + method.methodType().stringValue()).equals(violation.context())
                    && method.code().isPresent()) {
                CodeModel code = method.code().get();
                Map<Label, Integer> lines = CodeLines.atLabels(code);
                List<Integer> matching = code.exceptionHandlers().stream()
                        .filter(handler -> reason.equals(catchReason(handler)))
                        .map(handler -> lines.getOrDefault(handler.handler(), -1)).toList();
                return index < matching.size() && matching.get(index) >= 0 ? Optional.of(matching.get(index))
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static String catchReason(ExceptionCatch handler) {
        return handler.catchType().map(type -> ForbiddenTypes.checkInternalName(type.asInternalName())).orElse(null);
    }
}
