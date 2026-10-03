package javacardx.biometry1toN;

import javacard.framework.Shareable;

/**
 * A biometric matcher that other applets can use through the shareable interface mechanism.
 */
public interface SharedBioMatcher extends BioMatcher, Shareable {
}
