package javacardx.biometry;

import javacard.framework.Shareable;

/**
 * A biometric template that other applets can use for matching through the shareable interface mechanism.
 */
public interface SharedBioTemplate extends BioTemplate, Shareable {
}
