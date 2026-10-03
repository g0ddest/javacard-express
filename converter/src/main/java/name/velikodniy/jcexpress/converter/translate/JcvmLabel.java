package name.velikodniy.jcexpress.converter.translate;

/**
 * A position in symbolic JCVM code: the target of a branch or switch, or the boundary of an
 * exception handler range (JCVM 3.1 §6.10.3). Labels compare by identity; their byte offsets are
 * only known after {@link JcvmAssembler} has chosen the size of every instruction.
 */
public final class JcvmLabel {

    /** Creates a new, unbound label. */
    public JcvmLabel() {
        // identity object
    }
}
