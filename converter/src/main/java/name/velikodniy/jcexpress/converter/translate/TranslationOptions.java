package name.velikodniy.jcexpress.converter.translate;

/**
 * Options of the bytecode translation.
 *
 * @param supportInt32         whether the target supports the optional int type (JCVM 3.1 §2.2.3.1)
 * @param optimizePutfieldThis whether {@code putfield_<t>_this} (§7.5.76) may be used
 */
record TranslationOptions(boolean supportInt32, boolean optimizePutfieldThis) {}
