package name.velikodniy.jcexpress.livecard.junit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class that runs against the real card in the PC/SC reader.
 *
 * <p>The class is tagged {@value #TAG} (excluded from the normal build, run by {@code -Plivecard}) and uses
 * {@link LiveCardExtension}: it is skipped unless live-card tests are enabled and a card is present; test
 * methods, constructors and {@code @BeforeAll}/{@code @AfterAll} methods receive a connected
 * {@link name.velikodniy.jcexpress.livecard.LiveCard} parameter; every test gets its own APDU transcript;
 * everything the class created on the card is deleted after the class, also when tests fail.</p>
 *
 * <p>The class holds the JUnit resource lock {@value #CARD_LOCK} (READ_WRITE): in a parallel run, live-card classes
 * run one at a time, and all methods of a class run in the class's thread, because javax.smartcardio admits only
 * the thread that took exclusive access to the card. Do not use the card from other threads: a timeout with
 * {@code threadMode = SEPARATE_THREAD} is rejected, and so is a command inside {@code assertTimeoutPreemptively}; use
 * {@code @Timeout} in its default {@code SAME_THREAD} mode and {@code assertTimeout}.</p>
 *
 * <pre>{@code
 * @LiveCardTest
 * class MyAppletLiveTest {
 *     @Test
 *     void answersHello(LiveCard card) {
 *         card.session().select(card.aid("0101"));
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag(LiveCardTest.TAG)
@ExtendWith(LiveCardExtension.class)
@ResourceLock(value = LiveCardTest.CARD_LOCK, mode = ResourceAccessMode.READ_WRITE)
public @interface LiveCardTest {

    /** JUnit tag of live-card tests. */
    String TAG = "livecard";

    /**
     * The JUnit resource lock of the card in the reader ({@code @ResourceLock}): every live-card class holds it in
     * READ_WRITE mode, so they never run at the same time.
     */
    String CARD_LOCK = "jcx.card";
}
