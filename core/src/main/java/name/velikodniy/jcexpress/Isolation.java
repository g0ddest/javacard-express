package name.velikodniy.jcexpress;

/**
 * Lifetime of an applet instance that {@link InstallApplet} declares on a test class.
 *
 * <p>The package (load file) of an applet is loaded once for the test class on every backend; isolation decides
 * how long an <em>instance</em> lives. An instance declared on a test method always lives for that one test.</p>
 */
public enum Isolation {

    /**
     * A fresh instance for every test (the default): installed before the {@code @BeforeEach} methods run and
     * deleted after the {@code @AfterEach} methods, also when the test fails. Every invocation of a
     * {@code @ParameterizedTest} or {@code @RepeatedTest} counts as a test. The instance's fields start fresh;
     * the static fields of the applet's package keep their values for the test class, as on a card, where the
     * package stays loaded.
     */
    PER_TEST,

    /**
     * One instance for all tests of the class: installed before the {@code @BeforeAll} methods run and deleted
     * after the {@code @AfterAll} methods; the first PER_CLASS instance of the class is selected after the
     * installs, so the {@code @BeforeAll} methods talk to it. State carries over from one test to the next, so
     * order the tests (for example with {@code @TestMethodOrder}) when they depend on each other: persistent
     * fields always, CLEAR_ON_DESELECT memory (a verified PIN, a session) as long as the instance stays selected.
     * The instance is not selected again before a test while it is the selected applet; a SELECT of another
     * applet, {@code reset()}, {@code deselect()}, and the install or delete of another instance (card content
     * management deselects the selected applet, as on a GlobalPlatform card) end that.
     */
    PER_CLASS
}
