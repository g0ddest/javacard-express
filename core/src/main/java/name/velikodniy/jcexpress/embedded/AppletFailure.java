package name.velikodniy.jcexpress.embedded;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.InstallException;

/**
 * What an applet threw before jCardSim turned it into a status word ('6F00' for {@code process}, '6999' for
 * {@code select}, a {@code SystemException} for {@code install}): a Java Card runtime answers an uncaught exception
 * other than {@code ISOException} with '6F00' and does not pass it on (JCRE 3.0.5 chapter 3, the method process), so
 * the session notes it.
 *
 * @param failure     the exception
 * @param appletClass the applet class, whose package marks the frame the note names
 */
record AppletFailure(Throwable failure, Class<?> appletClass) {

    /**
     * Describes the failure for the history: {@code applet threw java.lang.ArrayIndexOutOfBoundsException: Index 5
     * out of bounds for length 4 at com.example.WalletApplet.process(WalletApplet.java:42)}, with the first frame
     * in the applet's package (the first frame when none is).
     *
     * @return the note
     */
    String describe() {
        return "applet threw " + thrownAt();
    }

    /**
     * Explains a failed install method that threw this failure.
     *
     * @param aid        the instance AID
     * @param parameters the install parameters the test passed
     * @param reported   what jCardSim's runtime threw (its cause is this failure)
     * @return the exception, with {@code reported} as its cause and {@link InstallException#sw()} 0
     */
    InstallException installException(AID aid, byte[] parameters, RuntimeException reported) {
        return new InstallException(appletClass.getName(), aid, parameters, 0, "its install method failed without an"
                + " ISOException: it threw " + thrownAt() + " (jCardSim reports any other exception of the install"
                + " method or the applet's constructor, and an install method that does not call register(), as"
                + " SystemException)", reported);
    }

    /** The exception and the frame of the applet's package: "java.lang.X: message at a.B.m(B.java:4)". */
    private String thrownAt() {
        StackTraceElement[] frames = failure.getStackTrace();
        String pkg = appletClass.getPackageName();
        StackTraceElement frame = frames.length == 0 ? null : frames[0];
        for (StackTraceElement candidate : frames) {
            if (packageOf(candidate.getClassName()).equals(pkg)) {
                frame = candidate;
                break;
            }
        }
        return failure + (frame == null ? "" : " at " + format(frame));
    }

    private static String packageOf(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? "" : className.substring(0, dot);
    }

    /** The frame as Java prints it, without the name of the session's class loader. */
    private static String format(StackTraceElement frame) {
        String location = frame.isNativeMethod() ? "Native Method" : frame.getFileName() == null ? "Unknown Source"
                : frame.getLineNumber() < 0 ? frame.getFileName() : frame.getFileName() + ":" + frame.getLineNumber();
        return frame.getClassName() + "." + frame.getMethodName() + "(" + location + ")";
    }
}
