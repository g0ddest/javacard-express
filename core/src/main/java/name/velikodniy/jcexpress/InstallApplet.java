package name.velikodniy.jcexpress;

import javacard.framework.Applet;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Installs an applet instance for a test class, a {@code @Nested} class or a test method, and deletes it
 * afterwards, also when a test fails. The same declaration works on jCardSim (the default), the simulated
 * GlobalPlatform card and a real card ({@code -Djcx.backend=...}, see {@link JavaCardTest}), with the same
 * selections: a declared install selects nothing, as INSTALL [for install and make selectable] through the Issuer
 * Security Domain does on a card (GPCS v2.3.1 11.5), installs and deletes deselect the selected applet on every
 * backend, and {@code card.install(...)} selects the instance it installs.
 *
 * <pre>
 * {@literal @}JavaCardTest
 * {@literal @}InstallApplet(CounterApplet.class)                      // a fresh instance for every test
 * class CounterAppletTest {
 *     {@literal @}Test
 *     void startsAtZero(SmartCardSession card) {
 *         assertThat(card.send(0x80, 0x02, 0, 0, null, 4)).isSuccess().dataEquals(0, 0, 0, 0);
 *     }
 * }
 * </pre>
 *
 * <h2>Lifetime</h2>
 * <ul>
 *   <li>On a class, the instance lives as {@link #isolation()} says: {@link Isolation#PER_TEST} (the default)
 *       gives every test a fresh instance, {@link Isolation#PER_CLASS} one instance for all tests of the class.
 *       {@code @Nested} classes see the instances of their enclosing classes and may declare their own, which
 *       are deleted when the nested class ends.</li>
 *   <li>On a test method, the instance exists only for that test (its {@code @BeforeEach} and {@code @AfterEach}
 *       methods included).</li>
 *   <li>After the {@link Isolation#PER_CLASS} instances of a class are installed, the first of them is
 *       selected, so the class's {@code @BeforeAll} methods talk to it (an applet that declines the selection
 *       stays unselected, and the history notes it).</li>
 *   <li>Before every test, the first applet declared by the innermost scope (the method, then the nearest
 *       class) is selected, unless it still is the selected applet of the basic channel: selected by the card or
 *       by a SELECT the test sent, with no other SELECT, {@code reset()}, {@code deselect()}, install or delete
 *       since. So a PER_CLASS instance keeps its CLEAR_ON_DESELECT memory (a verified PIN, a session) from one
 *       test to the next.</li>
 * </ul>
 *
 * <h2>AIDs</h2>
 * <p>Every AID the tests create starts with the run's AID prefix: {@code jcx.aidPrefix} when set (on a real card
 * the live-card setting {@code aidPrefix} wins), else the project's own prefix when the applet comes from a package
 * the Maven plugin built ({@link name.velikodniy.jcexpress.backend.AidScheme#forProject(String)}), else
 * {@value name.velikodniy.jcexpress.backend.AidScheme#DEFAULT_PREFIX}. The package and applet (module) AIDs are
 * derived from the package and class names, the same on every backend; the instance AID is the module AID unless
 * {@link #aid()} gives a suffix. Tests address applets by class ({@code card.select(CounterApplet.class)},
 * {@code card.aid(...)}) and contain no literal AIDs, so the same test runs on the simulator and on a card. A class
 * installed more than once is addressed by its suffix: {@code card.aid("0102")}; {@code card.aid(Class)} and
 * {@code card.select(Class)} then refuse to choose and list the instances.</p>
 *
 * <p>Using this annotation registers {@link JavaCardExtension}; {@link JavaCardTest} on the class is the
 * documented way to mark such a test class.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Repeatable(InstallApplets.class)
@ExtendWith(JavaCardExtension.class)
@ResourceLock(providers = CardLocks.class)
public @interface InstallApplet {

    /**
     * The applet class.
     *
     * @return the applet class (a subclass of {@code javacard.framework.Applet}), from the main or the test
     *         sources
     */
    Class<? extends Applet> value();

    /**
     * The instance AID as a hex suffix of the run's AID prefix; empty (the default) uses the applet's own AID:
     * the run's prefix and a suffix derived from the package and class names. Give it when the same applet is
     * installed more than once, so that a test names each instance by its suffix ({@code card.aid("0101")}).
     *
     * @return the instance AID suffix (hex), or empty
     */
    String aid() default "";

    /**
     * Install parameters: the application specific parameters the applet's {@code install} method receives (the
     * GlobalPlatform C9 value), as hex.
     *
     * @return the parameters (hex), or empty
     */
    String params() default "";

    /**
     * Lifetime of an instance declared on a class; ignored on a test method, whose instance lives for that test.
     *
     * @return the isolation, {@link Isolation#PER_TEST} by default
     */
    Isolation isolation() default Isolation.PER_TEST;
}
