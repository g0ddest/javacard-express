/**
 * Compile-only stubs of the GlobalPlatform Card API, package {@code org.globalplatform}, as of version 1.8 of the
 * package: what a GlobalPlatform card offers to its applets, such as the secure channel of the applet's Security
 * Domain ({@link org.globalplatform.GPSystem#getSecureChannel()}), the life cycle states of the applet and of the
 * card, the global PIN and the registry of the applications on the card.
 *
 * <p>The stubs carry the names, signatures and constant values an applet needs to compile; every method body
 * throws {@code RuntimeException("stub")}. On a card the package is part of the platform.</p>
 *
 * <p>The package has a version of its own, independent of the Java Card version: GlobalPlatform 2.1 and 2.1.1
 * cards have version 1.0, and 1.8 is the version described here. Every type and member says which version of the
 * package introduced it. That matters when an applet is converted: the CAP file names the version of each package
 * it imports, and a card links the applet only if its own {@code org.globalplatform} has the same major version
 * and a minor version that is not lower (JCVM 3.1 section 4.5.2). The converter of this project records the
 * lowest version that has every type and method the applet's package uses, so the first use of a type or method
 * of a later version raises the version a card must have. Constants do not count: the compiler copies their values
 * into the applet's class files.</p>
 */
package org.globalplatform;
