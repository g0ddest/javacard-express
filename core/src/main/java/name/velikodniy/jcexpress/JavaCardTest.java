package name.velikodniy.jcexpress;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class whose tests talk to Java Card applets declared with {@link InstallApplet}. The same class
 * runs on every backend; the backend is configuration, not code:
 *
 * <pre>
 * mvn verify                                  # jCardSim in the test JVM (the default)
 * mvn verify -Djcx.backend=simulated-gp       # converted, loaded and managed on a simulated GlobalPlatform card
 * mvn verify -Djcx.backend=livecard -Djcx.livecard.verifierSdk=&lt;Java Card kit&gt; -Dtest=WalletScenarioTest
 *                                             # the card in the PC/SC reader (see LIVE_CARD_TESTING.md)
 * </pre>
 *
 * <p>{@code jcx.backend} is read from the system property, then the JUnit configuration parameter, then the
 * environment variable {@code JCX_BACKEND}; {@code livecard} is accepted only as a JVM system property, so that no
 * file or environment can switch a build to the real card. See {@link Mode} for the backends; {@code container} is
 * not available for these classes yet (use {@code @SmartCard(mode = Mode.CONTAINER)} fields).</p>
 *
 * <p>Tests and lifecycle methods receive the card as a {@link SmartCardSession} parameter (or a {@link SmartCard}
 * field). One card serves the class and its {@code @Nested} classes; it is closed after the class, and on a
 * GlobalPlatform backend everything the tests created is deleted then and checked with GET STATUS. The methods of
 * a class run one at a time in its thread; with the {@code livecard} backend, test classes also run one at a
 * time, because they share the card.</p>
 *
 * <p>The card behaves the same on every backend: {@code send} completes '61XX' and '6CXX' as a PC/SC reader
 * does ({@link SW}); declared installs select nothing ({@code card.install(...)} selects the instance it installs),
 * the first PER_CLASS applet is selected for {@code @BeforeAll}, and before every test the nearest applet is
 * selected unless it still is ({@link InstallApplet}); {@code history()} holds the current test's exchanges;
 * failures of tests, lifecycle methods and declared installs carry the APDU transcript.</p>
 *
 * <p>With the {@code WalletApplet} of the testing cookbook (TESTING.md), whose install parameter is the starting
 * balance:</p>
 *
 * <pre>
 * {@literal @}JavaCardTest
 * {@literal @}InstallApplet(value = WalletApplet.class, params = "0064", isolation = Isolation.PER_CLASS)
 * {@literal @}TestMethodOrder(MethodOrderer.OrderAnnotation.class)
 * class WalletScenarioTest {
 *     {@literal @}Test {@literal @}Order(1)
 *     void credit(SmartCardSession card) {
 *         assertThat(card.send(0x80, 0x30, 0, 0, new byte[] {0, 100})).isSuccess();   // CREDIT 100
 *     }
 *
 *     {@literal @}Test {@literal @}Order(2)
 *     void balance(SmartCardSession card) {
 *         assertThat(card.send(0x80, 0x52, 0, 0, null, 2)).u16(0).isEqualTo(200);   // GET BALANCE
 *     }
 * }
 * </pre>
 *
 * <p>On the GlobalPlatform backends an applet's own commands avoid some INS values in a proprietary class (INS
 * {@code 50}, {@code 82}, {@code 70}, and {@code A4} with P1 {@code 04}), which the APDU guard reads as
 * GlobalPlatform authentication or as a change of the selection; see the README.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(JavaCardExtension.class)
@ResourceLock(providers = CardLocks.class)
public @interface JavaCardTest {
}
