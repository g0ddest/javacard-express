package name.velikodniy.jcexpress.converter.translate;

/**
 * Thrown when a method cannot be translated into correct JCVM bytecode.
 *
 * <p>The translator is fail-closed: an instruction or construct that has no exact JCVM
 * counterpart stops the conversion with this exception instead of being dropped or approximated.
 * The message names the class, the method and, when the class file has a LineNumberTable, the
 * source file and line, e.g.
 * {@code com/example/Wallet.process(Ljavacard/framework/APDU;)V (Wallet.java:42): ...}.
 * The converter reports it as a {@link name.velikodniy.jcexpress.converter.ConverterException}.
 */
public class TranslationException extends RuntimeException {

    private final String className;
    private final String method;
    private final int line;

    /**
     * Creates an exception for a located problem.
     *
     * @param className  internal name of the class
     * @param method     method name and descriptor
     * @param sourceFile source file name, or {@code null}
     * @param line       source line, or -1 if unknown
     * @param message    description of the problem with its JCVM specification reference
     * @param cause      underlying exception, or {@code null}
     */
    public TranslationException(String className, String method, String sourceFile, int line,
                                String message, Throwable cause) {
        super(location(className, method, sourceFile, line) + ": " + message, cause);
        this.className = className;
        this.method = method;
        this.line = line;
    }

    /**
     * Returns the internal name of the class whose method failed to translate.
     *
     * @return class name
     */
    public String className() {
        return className;
    }

    /**
     * Returns the name and descriptor of the method that failed to translate.
     *
     * @return method name and descriptor
     */
    public String method() {
        return method;
    }

    /**
     * Returns the source line of the failing instruction.
     *
     * @return line number, or -1 if unknown
     */
    public int line() {
        return line;
    }

    private static String location(String className, String method, String sourceFile, int line) {
        String where = className + "." + method;
        if (line < 0) {
            return where;
        }
        String file = sourceFile != null ? sourceFile : "line";
        return where + " (" + file + ":" + line + ")";
    }
}
