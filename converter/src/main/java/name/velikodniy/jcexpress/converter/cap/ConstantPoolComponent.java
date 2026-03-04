package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;

/**
 * Generates the CAP ConstantPool component (tag 5) as defined in JCVM 3.0.5 spec section 6.7.
 *
 * <p>The ConstantPool component contains the JCVM constant pool, which is fundamentally
 * different from the JVM constant pool. In the JCVM, every constant pool entry is
 * exactly 4 bytes: a 1-byte tag identifying the entry type, followed by 3 bytes of
 * type-specific data. There are no string or UTF-8 entries -- all references are
 * resolved to numeric tokens and offsets during conversion.
 *
 * <p>JCVM bytecodes reference constant pool entries by their zero-based index. The
 * bytecode translator replaces JVM-style symbolic references (class names, method
 * names) with JCVM constant pool indices during Stage 5 (bytecode translation),
 * and those indices point into this component.
 *
 * <p>The six entry types are:
 * <ul>
 *   <li><b>1 (CONSTANT_Classref)</b> -- reference to an internal or external class</li>
 *   <li><b>2 (CONSTANT_InstanceFieldref)</b> -- instance field: class ref + field token</li>
 *   <li><b>3 (CONSTANT_VirtualMethodref)</b> -- virtual method: class ref + method token</li>
 *   <li><b>4 (CONSTANT_SuperMethodref)</b> -- super method call: class ref + method token</li>
 *   <li><b>5 (CONSTANT_StaticFieldref)</b> -- static field: internal offset or external token</li>
 *   <li><b>6 (CONSTANT_StaticMethodref)</b> -- static method: internal offset or external token</li>
 * </ul>
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.7, Table 6-6):
 * <pre>
 * u1  tag = 5
 * u2  size
 * u2  count            (number of CP entries)
 * cp_info[count]:
 *   u1  tag            (entry type, 1-6)
 *   u1  info[3]        (type-specific data)
 * </pre>
 *
 * @see name.velikodniy.jcexpress.converter.translate.JcvmConstantPool
 * @see ImportComponent
 */
public final class ConstantPoolComponent {

    public static final int TAG = 5;

    private ConstantPoolComponent() {}

    /**
     * Generates the ConstantPool component bytes.
     *
     * @param cp the populated JCVM constant pool
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(JcvmConstantPool cp) {
        // --- constant_pool_component (§6.7 Table 6-6) ---
        var info = new BinaryWriter();
        info.u2(cp.size()); // §6.7: u2 count (number of cp_info entries)

        // §6.7 Table 6-7: each cp_info is exactly 4 bytes: u1 tag + u1[3] info
        for (JcvmConstantPool.CpEntry entry : cp.entries()) {
            info.bytes(entry.toBytes()); // §6.7: cp_info (tag + 3 bytes type-specific data)
        }

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }
}
