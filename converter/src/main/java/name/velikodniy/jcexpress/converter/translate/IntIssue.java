package name.velikodniy.jcexpress.converter.translate;

/**
 * A use of the int type or of a 32-bit intermediate value that the target cannot execute with
 * the same result as the Java virtual machine (JCVM 3.1 §2.2.3.1, §2.2.1.1.8).
 *
 * @param bci     JVM bytecode index of the offending instruction, or -1 for declarations
 * @param line    source line, or -1 if unknown
 * @param message description including the specification reference
 */
public record IntIssue(int bci, int line, String message) {}
