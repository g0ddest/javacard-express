package name.velikodniy.jcexpress.fakes.isolation;

import javacard.framework.Shareable;

/** Shareable interface of {@link LedgerApplet}. */
public interface Ledger extends Shareable {

    /**
     * Returns the balance of the ledger.
     *
     * @return the balance
     */
    short balance();
}
