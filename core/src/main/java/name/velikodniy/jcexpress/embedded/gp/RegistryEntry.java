package name.velikodniy.jcexpress.embedded.gp;

import javacard.framework.AID;
import javacard.framework.ISOException;
import org.globalplatform.GPRegistryEntry;

import java.util.OptionalInt;

/**
 * The registry entry an applet gets from {@code GPSystem.getRegistryEntry} (GlobalPlatform Card API 1.8,
 * interface GPRegistryEntry): a view of a {@link CardApplication} that applies the rules of the API to whoever
 * calls it. An entry is a Shareable Interface Object (GPCS v2.3.1 Annex A.1) and may be called by another
 * application than the one that obtained it, so the caller is determined at every call.
 */
final class RegistryEntry implements GPRegistryEntry {

    private static final int SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982;
    private static final int SW_CONDITIONS_NOT_SATISFIED = 0x6985;
    private static final int SW_REFERENCED_DATA_NOT_FOUND = 0x6A88;

    private final GlobalPlatformCard card;
    private final CardApplication application;
    private AID aid;

    RegistryEntry(GlobalPlatformCard card, CardApplication application) {
        this.card = card;
        this.application = application;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The reason codes are not specified; this card answers '6982' when the caller is not the application of
     * the entry or lacks the Global Service privilege, and '6A88' when it did not register the name.</p>
     */
    @Override
    public void deregisterService(short sServiceName) throws ISOException {
        requireGlobalServicesApplication();
        if (!card.services().deregister(application.aid(), sServiceName)) {
            ISOException.throwIt((short) SW_REFERENCED_DATA_NOT_FOUND);
        }
    }

    @Override
    public AID getAID() {
        if (aid == null) {
            byte[] bytes = application.aid().toBytes();
            aid = new AID(bytes, (short) 0, (byte) bytes.length);
        }
        return aid;
    }

    @Override
    public short getPrivileges(byte[] baBuffer, short sOffset) throws ArrayIndexOutOfBoundsException {
        byte[] privileges = application.privileges();
        if (sOffset < 0 || sOffset + privileges.length > baBuffer.length) {
            throw new ArrayIndexOutOfBoundsException("The privilege bytes do not fit at offset " + sOffset);
        }
        System.arraycopy(privileges, 0, baBuffer, sOffset, privileges.length);
        return (short) (sOffset + privileges.length);
    }

    @Override
    public byte getState() {
        return (byte) application.state();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Every application of the simulated card is associated with its one Security Domain.</p>
     */
    @Override
    public boolean isAssociated(AID sdAID) {
        return sdAID != null && card.securityDomain().aid().equals(GlobalPlatformCard.aidOf(sdAID));
    }

    @Override
    public boolean isPrivileged(byte bPrivilege) {
        return application.hasPrivilege(bPrivilege);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The reason codes are not specified; this card answers '6982' when the caller is not the application of
     * the entry or lacks the Global Service privilege, and '6985' when the name is taken.</p>
     */
    @Override
    public void registerService(short sServiceName) throws ISOException {
        requireGlobalServicesApplication();
        if (!card.services().register(application.aid(), sServiceName)) {
            ISOException.throwIt((short) SW_CONDITIONS_NOT_SATISFIED);
        }
    }

    @Override
    public boolean setState(byte bState) {
        name.velikodniy.jcexpress.AID caller = card.caller();
        boolean itself = application.aid().equals(caller);
        boolean globalLock = card.application(caller).map(a -> a.hasPrivilege(PRIVILEGE_GLOBAL_LOCK)).orElse(false);
        OptionalInt next = LifeCycle.throughEntry(application.state(), bState & 0xFF, itself, globalLock);
        next.ifPresent(application::state);
        return next.isPresent();
    }

    /** Only the application of the entry, with the Global Service privilege, manages its service names. */
    private void requireGlobalServicesApplication() {
        boolean itself = application.aid().equals(card.caller());
        if (!itself || !application.hasPrivilege(PRIVILEGE_GLOBAL_SERVICE)) {
            ISOException.throwIt((short) SW_SECURITY_STATUS_NOT_SATISFIED);
        }
    }
}
