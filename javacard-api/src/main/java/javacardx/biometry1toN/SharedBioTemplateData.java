package javacardx.biometry1toN;

import javacard.framework.Shareable;

/**
 * Biometric template data that other applets can read through the shareable interface mechanism.
 */
public interface SharedBioTemplateData extends BioTemplateData, Shareable {
}
