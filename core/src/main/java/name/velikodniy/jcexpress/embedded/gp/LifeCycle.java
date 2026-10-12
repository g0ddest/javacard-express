package name.velikodniy.jcexpress.embedded.gp;

import java.util.OptionalInt;

/**
 * The life cycle transitions an application may ask for (GlobalPlatform Card Specification v2.3.1 section 5.3.1
 * and Table 11-4; GlobalPlatform Card API 1.8, GPSystem.setCardContentState and GPRegistryEntry.setState).
 */
final class LifeCycle {

    private static final int LOCKED = CardApplication.LOCKED;
    private static final int APPLICATION_SPECIFIC_MASK = 0x87;

    private LifeCycle() {
    }

    /**
     * Tells whether a state is an application specific state: b8 clear and the three low bits set, '07' (SELECTABLE)
     * to '7F' (Table 11-4).
     *
     * @param state the state byte, 0 to 255
     * @return true for an application specific state
     */
    static boolean applicationSpecific(int state) {
        return (state & APPLICATION_SPECIFIC_MASK) == CardApplication.SELECTABLE;
    }

    /**
     * The transition an application asks for itself (GPSystem.setCardContentState): from an application specific
     * state, so neither from INSTALLED nor when locked, to another application specific state, or to locked when
     * b8 of the requested state is set. The locked state keeps the bits of the state before, to which the card
     * returns when the application is unlocked (GPCS v2.3.1 5.3.1.3).
     *
     * @param current   the state of the application, 0 to 255
     * @param requested the requested state, 0 to 255
     * @return the new state, or empty when the request is rejected
     */
    static OptionalInt ofItself(int current, int requested) {
        if (!applicationSpecific(current)) {
            return OptionalInt.empty();
        }
        if ((requested & LOCKED) != 0) {
            return OptionalInt.of(current | LOCKED);
        }
        return applicationSpecific(requested) ? OptionalInt.of(requested) : OptionalInt.empty();
    }

    /**
     * The transition asked through a registry entry (GPRegistryEntry.setState). For a locked application only b8
     * counts: clear, it is a request to unlock, which needs the Global Lock privilege and returns to the state
     * before. With b8 set it is a request to lock, for the application itself or a caller with the Global Lock
     * privilege. Any other request is a move between application specific states, which only the application
     * itself makes (GPCS v2.3.1 Figure 5-2); INSTALLED to SELECTABLE is never made this way.
     *
     * @param current    the state of the application of the entry, 0 to 255
     * @param requested  the requested state, 0 to 255
     * @param itself     whether the caller is the application of the entry
     * @param globalLock whether the caller has the Global Lock privilege
     * @return the new state, or empty when the request is rejected or changes nothing
     */
    static OptionalInt throughEntry(int current, int requested, boolean itself, boolean globalLock) {
        boolean lock = (requested & LOCKED) != 0;
        if ((current & LOCKED) != 0) {
            return !lock && globalLock ? OptionalInt.of(current & ~LOCKED) : OptionalInt.empty();
        }
        if (lock) {
            return itself || globalLock ? OptionalInt.of(current | LOCKED) : OptionalInt.empty();
        }
        return itself && applicationSpecific(current) && applicationSpecific(requested) && requested != current
                ? OptionalInt.of(requested) : OptionalInt.empty();
    }
}
