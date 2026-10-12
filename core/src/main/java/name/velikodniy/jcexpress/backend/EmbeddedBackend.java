package name.velikodniy.jcexpress.backend;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SecurityDomainKeys;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.embedded.gp.GlobalPlatformCard;
import name.velikodniy.jcexpress.embedded.gp.SecurityDomain;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * The default backend: jCardSim in the test JVM ({@link EmbeddedSession}). Each test class run gets a new
 * simulator that loads the applet classes itself, so static fields start fresh for the class and keep their values
 * across the tests of the class, as on a card where the package stays loaded; a deleted instance can be installed
 * again.
 *
 * <p>Installs and deletes behave as card content management on a GlobalPlatform card, where the Issuer Security
 * Domain handles them on the basic channel (GPCS v2.3.1 11.5 INSTALL, 11.2 DELETE): the selected applet is deselected
 * first, and the installed applet is not selected. So an applet's {@code select()} and {@code deselect()} run as
 * often as on the simulated GlobalPlatform card and on a real one.</p>
 *
 * <p>Tests can declare a {@link GlobalPlatformCard} parameter: the GlobalPlatform environment of the simulated
 * card, which applets reach through {@code org.globalplatform.GPSystem}; and a {@link SecurityDomainKeys}
 * parameter: the keys of its Security Domain, for a secure channel opened through an applet.</p>
 */
public final class EmbeddedBackend implements CardBackend {

    /** The AID of the empty applet that {@link TestCard#deselect()} selects on jCardSim. */
    static final AID IDLE = AID.fromHex("A000000000FFFFFF");

    /** Creates the backend ({@link java.util.ServiceLoader} instantiates it). */
    public EmbeddedBackend() {
        // stateless
    }

    @Override
    public Mode mode() {
        return Mode.EMBEDDED;
    }

    @Override
    public Set<Class<?>> parameterTypes() {
        return Set.of(GlobalPlatformCard.class, SecurityDomainKeys.class);
    }

    @Override
    public TestCard open(CardRequest request) {
        return new Card(request.aidScheme());
    }

    /** A jCardSim card for one test class run. */
    private static final class Card implements TestCard {
        private final AidScheme aids;
        private final EmbeddedSession session = new EmbeddedSession();
        private boolean idleInstalled;

        Card(AidScheme aids) {
            this.aids = aids;
        }

        @Override
        public Mode mode() {
            return Mode.EMBEDDED;
        }

        @Override
        public AidScheme aids() {
            return aids;
        }

        @Override
        public SmartCardSession session() {
            return session;
        }

        @Override
        public void install(AppletDeclaration applet, Collection<AppletDeclaration> declaredRun) {
            session.deselect();
            session.installWithoutSelecting(applet.appletClass(), applet.instanceAid(), applet.parameters());
        }

        @Override
        public void delete(AppletDeclaration applet) {
            session.deselect();
            session.delete(applet.instanceAid());
        }

        @Override
        public void deselect() {
            if (!idleInstalled) {
                session.install(IdleApplet.class, IDLE);
                idleInstalled = true;
            }
            session.select(IDLE);
        }

        @Override
        public <T> Optional<T> resolve(Class<T> type) {
            if (type == GlobalPlatformCard.class) {
                return Optional.of(type.cast(session.globalPlatform()));
            }
            if (type == SecurityDomainKeys.class) {
                SecurityDomain domain = session.globalPlatform().securityDomain();
                return Optional.of(type.cast(new SecurityDomainKeys(domain.keyEnc(), domain.keyMac(),
                        domain.keyDek())));
            }
            return Optional.empty();
        }

        @Override
        public void close() {
            session.close();
        }
    }
}
