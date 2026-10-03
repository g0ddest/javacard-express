package name.velikodniy.jcexpress.livecard.model.samepackage;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * A {@code @JavaCardTest} class in the package of its test-source applet, with code outside the Java Card subset
 * (strings, a lambda): the GlobalPlatform backends convert the applet and the classes it uses, not this class. Run
 * by {@code GpBackendParityTest} through the JUnit Platform test kit (its name does not match the test patterns of
 * Maven Surefire).
 */
@JavaCardTest
@InstallApplet(SamePackageApplet.class)
public class SamePackageScenario {

    /** What the test saw, in order. */
    public static final List<String> SEEN = new CopyOnWriteArrayList<>();

    @Test
    void answers(SmartCardSession card) {
        Supplier<String> answer = () -> card.send(0x80, 0x0E, 0, 0, null, 2).dataAsString();
        SEEN.add("answered " + answer.get());
    }
}
