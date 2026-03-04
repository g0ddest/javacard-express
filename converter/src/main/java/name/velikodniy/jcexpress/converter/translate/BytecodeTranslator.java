package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.resolve.CpReference;
import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;

import java.io.ByteArrayOutputStream;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static name.velikodniy.jcexpress.converter.translate.JcvmOpcode.*;

/**
 * Translates JVM bytecode instructions into JCVM bytecode for inclusion in the
 * Method component of a CAP file.
 *
 * <p>This class implements <b>Stage 5: Bytecode Translation</b> of the converter pipeline.
 * It sits between the reference resolution stage ({@link ReferenceResolver}) and the CAP
 * component assembly stage ({@link name.velikodniy.jcexpress.converter.cap.MethodComponent}).
 * For each method body, it walks the JDK {@link java.lang.classfile.CodeModel} instruction
 * stream and emits the equivalent JCVM instruction sequence defined by the
 * <i>Java Card Virtual Machine Specification 3.0.5, Chapter 7 (JCVM Instruction Set)</i>.
 *
 * <h2>Translation Rules</h2>
 * <ul>
 *   <li><b>Type narrowing</b>: JVM {@code int} operations are mapped to JCVM {@code short}
 *       operations by default (e.g. {@code iconst} to {@code sconst}, {@code iadd} to
 *       {@code sadd}). When the ACC_INT flag is active, the original 32-bit opcodes are
 *       preserved (JCVM spec 7.2).</li>
 *   <li><b>Field access</b>: JVM generic {@code getfield}/{@code putfield} instructions are
 *       split into type-specific variants ({@code getfield_a}, {@code getfield_b},
 *       {@code getfield_s}, {@code getfield_i}) based on field descriptor analysis.</li>
 *   <li><b>Method invocation</b>: constant pool references are resolved to JCVM token-based
 *       indices via {@link ReferenceResolver}, distinguishing between internal offsets and
 *       external package/class/method tokens (JCVM spec 6.8).</li>
 *   <li><b>Branch offsets</b>: narrow (1-byte signed) form is preferred; an
 *       {@link IllegalStateException} is thrown if the offset exceeds [-128, +127] since
 *       JCVM methods are typically small enough for narrow branches.</li>
 *   <li><b>{@code _THIS} optimization</b>: sequences of {@code ALOAD_0 + GETFIELD_x} and
 *       {@code ALOAD_0 + value_push + PUTFIELD_x} are collapsed into single
 *       {@code GETFIELD_x_THIS} / {@code PUTFIELD_x_THIS} opcodes per JCVM spec 7.5.8-9,
 *       saving one byte per field access on {@code this}.</li>
 * </ul>
 *
 * <h2>Translation Modes</h2>
 * <ul>
 *   <li><b>With resolver</b> ({@link #translate(MethodModel, ClassModel, ReferenceResolver)}):
 *       emits real constant pool indices and tracks their byte positions for the
 *       {@link name.velikodniy.jcexpress.converter.cap.RefLocationComponent}. This is the
 *       production mode used during CAP file generation.</li>
 *   <li><b>Without resolver</b> ({@link #translate(MethodModel, ClassModel, JcvmConstantPool)}):
 *       emits placeholder zeros for all CP references. This mode is used for unit testing
 *       bytecode translation logic in isolation, without requiring a full reference resolution
 *       context.</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe. All mutable state is encapsulated in the
 * internal {@code TranslationContext}, which is created fresh for each method translation.
 *
 * @see JcvmOpcode
 * @see TranslatedMethod
 * @see ReferenceResolver
 * @see name.velikodniy.jcexpress.converter.cap.MethodComponent
 */
public final class BytecodeTranslator {

    private BytecodeTranslator() {}

    /**
     * Translates a method with full reference resolution.
     * CP references are resolved via the provided {@link ReferenceResolver}
     * and their positions are tracked for the RefLocation component.
     *
     * @param method       the JVM method model
     * @param classModel   the enclosing class model
     * @param resolver     reference resolver for CP entry creation
     * @return translated method with real CP indices
     */
    public static TranslatedMethod translate(MethodModel method, ClassModel classModel,
                                             ReferenceResolver resolver) {
        return translate(method, classModel, resolver, false, true);
    }

    /**
     * Translates a method with full reference resolution and optional ACC_INT support.
     * When {@code supportInt32} is true, int-specific JCVM opcodes (iadd, imul, etc.)
     * are emitted instead of their short equivalents.
     *
     * @param method              the JVM method model
     * @param classModel          the enclosing class model
     * @param resolver            reference resolver for CP entry creation
     * @param supportInt32        if true, emit int-specific opcodes per JCVM spec 7.2
     * @param optimizePutfieldThis if true, apply PUTFIELD_x_THIS optimization (§7.5.63).
     *                            Oracle converters prior to JC 3.0.5 only apply GETFIELD_x_THIS;
     *                            set to false for binary compatibility with older JC versions.
     * @return translated method with real CP indices
     */
    public static TranslatedMethod translate(MethodModel method, ClassModel classModel,
                                             ReferenceResolver resolver, boolean supportInt32,
                                             boolean optimizePutfieldThis) {
        var code = method.code().orElse(null);
        if (code == null) {
            // Abstract/native method — compute nargs for the standard 2-byte header
            int nargs = TranslationContext.countArgs(method.methodType().stringValue());
            if ((method.flags().flagsMask() & 0x0008) == 0) {
                nargs++; // add 'this' for instance methods
            }
            return new TranslatedMethod(new byte[0], 0, 0, nargs, List.of(), false, List.of());
        }

        var ctx = new TranslationContext(
                resolver.constantPool(), classModel, resolver, supportInt32, optimizePutfieldThis);
        ctx.translate(code);
        return ctx.build(method);
    }

    /**
     * Translates a method without reference resolution (placeholder mode).
     * CP references are emitted as zeros. Suitable for testing bytecode
     * translation logic in isolation.
     *
     * @param method     the JVM method model
     * @param classModel the enclosing class model
     * @param cp         the JCVM constant pool builder
     * @return translated method with placeholder CP indices
     */
    public static TranslatedMethod translate(MethodModel method, ClassModel classModel,
                                             JcvmConstantPool cp) {
        var code = method.code().orElse(null);
        if (code == null) {
            int nargs = TranslationContext.countArgs(method.methodType().stringValue());
            if ((method.flags().flagsMask() & 0x0008) == 0) {
                nargs++;
            }
            return new TranslatedMethod(new byte[0], 0, 0, nargs, List.of(), false, List.of());
        }

        var ctx = new TranslationContext(cp, classModel, null, false, true);
        ctx.translate(code);
        return ctx.build(method);
    }

    /**
     * Determines the JCVM field type suffix from a field descriptor.
     *
     * @return 'a' for reference, 'b' for byte/boolean, 's' for short/char, 'i' for int
     */
    static char fieldTypeSuffix(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'L', '[' -> 'a'; // reference or array
            case 'B', 'Z' -> 'b'; // byte or boolean
            case 'I' -> 'i';      // int
            default -> 's';       // short, char, and everything else
        };
    }

    private static final class TranslationContext {
        private final JcvmConstantPool cp;
        private final ClassModel classModel;
        private final ReferenceResolver resolver; // null in placeholder mode
        private final boolean supportInt32;
        private final boolean optimizePutfieldThis;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final Map<Label, Integer> labelPositions = new HashMap<>();
        private final List<Fixup> fixups = new ArrayList<>();
        private final List<TranslatedMethod.JcvmExceptionHandler> handlers = new ArrayList<>();
        private final List<CpReference> cpReferences = new ArrayList<>();

        // _THIS optimization state: defer ALOAD_0 to detect GETFIELD/PUTFIELD on 'this'
        private boolean aload0Deferred = false;    // ALOAD_0 pending (not yet emitted)
        private boolean aload0ValuePushed = false;  // ALOAD_0 pending + one value push emitted
        private int valuePushCheckpoint = -1;       // byte position before the value push

        TranslationContext(JcvmConstantPool cp, ClassModel classModel,
                           ReferenceResolver resolver, boolean supportInt32,
                           boolean optimizePutfieldThis) {
            this.cp = cp;
            this.classModel = classModel;
            this.resolver = resolver;
            this.supportInt32 = supportInt32;
            this.optimizePutfieldThis = optimizePutfieldThis;
        }

        void translate(CodeModel code) {
            // Pre-register catch type ClassRefs before bytecode translation.
            // Oracle's converter resolves exception handler catch types before processing
            // bytecode, so catch type ClassRef entries appear earlier in the CP.
            preRegisterCatchTypes(code);

            for (CodeElement element : code) {
                switch (element) {
                    case Label label -> {
                        // Flush deferred ALOAD_0 at branch targets for safety
                        flushDeferredAload0();
                        labelPositions.put(label, out.size());
                    }
                    case ExceptionCatch ignored -> {
                        // Handled after all instructions are emitted
                    }
                    // JVM nop (0x00) → JCVM nop (0x00) -- §7.5.57: identical semantics
                    case NopInstruction ignored -> { flushDeferredAload0(); emit(NOP); }
                    case ConstantInstruction ci -> translateConstant(ci);
                    case LoadInstruction li -> translateLoad(li);
                    case StoreInstruction si -> translateStore(si);
                    case IncrementInstruction ii -> translateIncrement(ii);
                    case ArrayLoadInstruction ai -> translateArrayLoad(ai);
                    case ArrayStoreInstruction asi -> translateArrayStore(asi);
                    case StackInstruction si -> translateStack(si);
                    case OperatorInstruction oi -> translateOperator(oi);
                    case ConvertInstruction ci -> translateConvert(ci);
                    case BranchInstruction bi -> translateBranch(bi);
                    case ReturnInstruction ri -> translateReturn(ri);
                    case FieldInstruction fi -> translateField(fi);
                    case InvokeInstruction ii -> translateInvoke(ii);
                    case NewObjectInstruction ni -> translateNewObject(ni);
                    case NewPrimitiveArrayInstruction pi -> translateNewArray(pi);
                    case NewReferenceArrayInstruction ri -> translateANewArray(ri);
                    case TypeCheckInstruction ti -> translateTypeCheck(ti);
                    // JVM athrow (0xBF) → JCVM athrow (0x93) -- §7.5.7: throw exception
                    case ThrowInstruction ignored -> { flushDeferredAload0(); emit(ATHROW); }
                    // JVM monitorenter/monitorexit (0xC2/0xC3) → not supported in JCVM
                    // -- JCVM spec does not define monitor instructions
                    case MonitorInstruction ignored -> {
                        throw new IllegalStateException("monitorenter/monitorexit not supported");
                    }
                    case TableSwitchInstruction tsi -> translateTableSwitch(tsi);
                    case LookupSwitchInstruction lsi -> translateLookupSwitch(lsi);
                    default -> {
                        flushDeferredAload0();
                        // Handle simple opcodes that don't have specific instruction subtypes
                        if (element instanceof java.lang.classfile.Instruction inst) {
                            // JVM arraylength (0xBE) → JCVM arraylength (0x92) -- §7.5.5
                            if (inst.opcode() == java.lang.classfile.Opcode.ARRAYLENGTH) {
                                emit(ARRAYLENGTH);
                            }
                        }
                        // Skip line numbers, local variables, stack map frames, etc.
                    }
                }
            }

            flushDeferredAload0(); // flush any trailing deferred ALOAD_0
            resolveFixups();
            buildExceptionHandlers(code);
        }

        TranslatedMethod build(MethodModel method) {
            byte[] bytecode = out.toByteArray();
            var code = method.code().orElse(null);
            int maxStack = 0;
            int jvmMaxLocals = 0;
            if (code instanceof CodeAttribute ca) {
                maxStack = ca.maxStack();
                jvmMaxLocals = ca.maxLocals();
            }

            int nargs = countArgs(method.methodType().stringValue());
            if ((method.flags().flagsMask() & 0x0008) == 0) {
                nargs++; // add 'this' for non-static methods
            }

            // JCVM max_locals excludes method parameters (JCVM 3.0.5 §6.11)
            // JVM max_locals includes parameters, so: jcvm_locals = jvm_locals - nargs
            int maxLocals = Math.max(0, jvmMaxLocals - nargs);

            boolean isExtended = maxStack > 15 || maxLocals > 15 || nargs > 15;
            return new TranslatedMethod(bytecode, maxStack, maxLocals, nargs,
                    List.copyOf(handlers), isExtended, List.copyOf(cpReferences));
        }

        // ── Instruction translators ──

        private void translateConstant(ConstantInstruction ci) {
            // Constants are single-value pushes — eligible for PUTFIELD_THIS
            if (aload0Deferred && !aload0ValuePushed) {
                enterValuePushedState();
            } else {
                flushDeferredAload0();
            }
            var value = ci.constantValue();
            switch (ci.opcode()) {
                // JVM aconst_null (0x01) → JCVM aconst_null (0x01) -- §7.5.1: identical semantics
                case ACONST_NULL -> emit(ACONST_NULL);
                // JVM iconst_m1 (0x02) → JCVM sconst_m1 (0x02) or iconst_m1 (0x09)
                // -- §7.5.72/§7.5.39: int narrowed to short without ACC_INT
                case ICONST_M1 -> emit(supportInt32 ? ICONST_M1 : SCONST_M1);
                // JVM iconst_0 (0x03) → JCVM sconst_0 (0x03) or iconst_0 (0x0A) -- §7.5.72/§7.5.39
                case ICONST_0 -> emit(supportInt32 ? ICONST_0 : SCONST_0);
                // JVM iconst_1 (0x04) → JCVM sconst_1 (0x04) or iconst_1 (0x0B) -- §7.5.72/§7.5.39
                case ICONST_1 -> emit(supportInt32 ? ICONST_1 : SCONST_1);
                // JVM iconst_2 (0x05) → JCVM sconst_2 (0x05) or iconst_2 (0x0C) -- §7.5.72/§7.5.39
                case ICONST_2 -> emit(supportInt32 ? ICONST_2 : SCONST_2);
                // JVM iconst_3 (0x06) → JCVM sconst_3 (0x06) or iconst_3 (0x0D) -- §7.5.72/§7.5.39
                case ICONST_3 -> emit(supportInt32 ? ICONST_3 : SCONST_3);
                // JVM iconst_4 (0x07) → JCVM sconst_4 (0x07) or iconst_4 (0x0E) -- §7.5.72/§7.5.39
                case ICONST_4 -> emit(supportInt32 ? ICONST_4 : SCONST_4);
                // JVM iconst_5 (0x08) → JCVM sconst_5 (0x08) or iconst_5 (0x0F) -- §7.5.72/§7.5.39
                case ICONST_5 -> emit(supportInt32 ? ICONST_5 : SCONST_5);
                // JVM bipush (0x10) → JCVM bspush (0x10) -- §7.5.3: push byte as short
                case BIPUSH -> {
                    emit(BSPUSH);
                    emit(((Number) value).byteValue());
                }
                // JVM sipush (0x11) → JCVM sspush (0x11) -- §7.5.80: push short value
                case SIPUSH -> {
                    emit(SSPUSH);
                    emitShort(((Number) value).shortValue());
                }
                // JVM ldc/ldc_w (0x12/0x13) → JCVM bspush/sspush/iipush
                // -- §7.5.3/§7.5.80/§7.5.40: no CP-based ldc in JCVM; inline the int value
                case LDC, LDC_W -> {
                    if (value instanceof Integer i) {
                        if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
                            emit(BSPUSH);
                            emit(i.byteValue());
                        } else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
                            emit(SSPUSH);
                            emitShort(i.shortValue());
                        } else {
                            // JCVM iipush (0x14) -- §7.5.40: push 32-bit int immediate
                            emit(IIPUSH);
                            emitInt(i);
                        }
                    }
                    // String/Class constants not supported in JavaCard
                }
                default -> {}
            }
        }

        private void translateLoad(LoadInstruction li) {
            var slot = li.slot();
            switch (li.typeKind()) {
                // JVM aload/aload_<n> (0x19/0x2A-0x2D) → JCVM aload/aload_<n> (0x15/0x18-0x1B)
                // -- §7.5.2: load reference from local variable
                case REFERENCE -> {
                    if (slot == 0) {
                        // Defer ALOAD_0 for potential _THIS optimization
                        flushDeferredAload0(); // flush any prior deferred
                        aload0Deferred = true;
                    } else {
                        // Non-zero slot: this is a simple value push
                        if (aload0Deferred && !aload0ValuePushed) {
                            enterValuePushedState();
                        } else {
                            flushDeferredAload0();
                        }
                        if (slot <= 3) emit(ALOAD_0 + slot);
                        else { emit(ALOAD); emit(slot); }
                    }
                }
                // JVM iload/iload_<n> (0x15/0x1A-0x1D) → JCVM sload/sload_<n> (0x16/0x1C-0x1F)
                // or iload/iload_<n> (0x17/0x20-0x23) -- §7.5.75/§7.5.43: int narrowed to short
                // without ACC_INT
                case INT -> {
                    if (aload0Deferred && !aload0ValuePushed) {
                        enterValuePushedState();
                    } else {
                        flushDeferredAload0();
                    }
                    if (supportInt32) {
                        if (slot <= 3) emit(ILOAD_0 + slot);
                        else { emit(ILOAD); emit(slot); }
                    } else {
                        if (slot <= 3) emit(SLOAD_0 + slot);
                        else { emit(SLOAD); emit(slot); }
                    }
                }
                // JVM short/char/byte/boolean loads → JCVM sload/sload_<n> (0x16/0x1C-0x1F)
                // -- §7.5.75: all sub-int types use short load
                default -> {
                    if (aload0Deferred && !aload0ValuePushed) {
                        enterValuePushedState();
                    } else {
                        flushDeferredAload0();
                    }
                    if (slot <= 3) emit(SLOAD_0 + slot);
                    else { emit(SLOAD); emit(slot); }
                }
            }
        }

        private void translateStore(StoreInstruction si) {
            flushDeferredAload0();
            var slot = si.slot();
            switch (si.typeKind()) {
                // JVM astore/astore_<n> (0x3A/0x4B-0x4E) → JCVM astore/astore_<n> (0x28/0x2B-0x2E)
                // -- §7.5.6: store reference into local variable
                case REFERENCE -> {
                    if (slot <= 3) emit(ASTORE_0 + slot);
                    else { emit(ASTORE); emit(slot); }
                }
                // JVM istore/istore_<n> (0x36/0x3B-0x3E) → JCVM sstore/sstore_<n> (0x29/0x2F-0x32)
                // or istore/istore_<n> (0x2A/0x33-0x36) -- §7.5.82/§7.5.49: int narrowed to short
                // without ACC_INT
                case INT -> {
                    if (supportInt32) {
                        if (slot <= 3) emit(ISTORE_0 + slot);
                        else { emit(ISTORE); emit(slot); }
                    } else {
                        if (slot <= 3) emit(SSTORE_0 + slot);
                        else { emit(SSTORE); emit(slot); }
                    }
                }
                // JVM short/char/byte/boolean stores → JCVM sstore/sstore_<n> (0x29/0x2F-0x32)
                // -- §7.5.82: all sub-int types use short store
                default -> {
                    if (slot <= 3) emit(SSTORE_0 + slot);
                    else { emit(SSTORE); emit(slot); }
                }
            }
        }

        // JVM iinc (0x84) → JCVM sinc (0x59) or iinc (0x5A)
        // -- §7.5.74/§7.5.42: increment local variable; sinc_w (0x96) / iinc_w (0x97) for
        // wide constant values -- §7.5.74/§7.5.42: int narrowed to short without ACC_INT
        private void translateIncrement(IncrementInstruction ii) {
            flushDeferredAload0();
            int slot = ii.slot();
            int constant = ii.constant();
            if (supportInt32) {
                if (constant >= Byte.MIN_VALUE && constant <= Byte.MAX_VALUE) {
                    emit(IINC);
                    emit(slot);
                    emit(constant);
                } else {
                    emit(IINC_W);
                    emit(slot);
                    emitShort((short) constant);
                }
            } else {
                if (constant >= Byte.MIN_VALUE && constant <= Byte.MAX_VALUE) {
                    emit(SINC);
                    emit(slot);
                    emit(constant);
                } else {
                    emit(SINC_W);
                    emit(slot);
                    emitShort((short) constant);
                }
            }
        }

        // JVM aaload/baload/saload/caload/iaload (0x32/0x33/0x35/0x34/0x2E)
        // → JCVM aaload (0x24) / baload (0x25) / saload (0x26) / iaload (0x27)
        // -- §7.5.1/§7.5.8/§7.5.68/§7.5.37: type-specific array load;
        // char arrays use saload per §7.5.68 (char is 16-bit, same as short)
        private void translateArrayLoad(ArrayLoadInstruction ai) {
            flushDeferredAload0();
            switch (ai.typeKind()) {
                case REFERENCE -> emit(AALOAD);
                case BYTE, BOOLEAN -> emit(BALOAD);
                case SHORT, CHAR -> emit(SALOAD);
                case INT -> emit(IALOAD);
                default -> emit(SALOAD);
            }
        }

        // JVM aastore/bastore/sastore/castore/iastore (0x53/0x54/0x56/0x55/0x4F)
        // → JCVM aastore (0x37) / bastore (0x38) / sastore (0x39) / iastore (0x3A)
        // -- §7.5.1/§7.5.9/§7.5.69/§7.5.38: type-specific array store;
        // char arrays use sastore per §7.5.69 (char is 16-bit, same as short)
        private void translateArrayStore(ArrayStoreInstruction asi) {
            flushDeferredAload0();
            switch (asi.typeKind()) {
                case REFERENCE -> emit(AASTORE);
                case BYTE, BOOLEAN -> emit(BASTORE);
                case SHORT, CHAR -> emit(SASTORE);
                case INT -> emit(IASTORE);
                default -> emit(SASTORE);
            }
        }

        // JVM stack manipulation → JCVM stack manipulation
        // -- §7.5.15-18: JCVM uses generalized dup_x/swap_x with mn operand byte
        private void translateStack(StackInstruction si) {
            flushDeferredAload0();
            switch (si.opcode()) {
                // JVM pop (0x57) → JCVM pop (0x3B) -- §7.5.59
                case POP -> emit(POP);
                // JVM pop2 (0x58) → JCVM pop2 (0x3C) -- §7.5.60
                case POP2 -> emit(POP2);
                // JVM dup (0x59) → JCVM dup (0x3D) -- §7.5.15
                case DUP -> emit(DUP);
                // JVM dup2 (0x5C) → JCVM dup2 (0x3E) -- §7.5.16
                case DUP2 -> emit(DUP2);
                // JVM dup_x1 (0x5A) → JCVM dup_x (0x3F) mn=0x11 -- §7.5.17: m=1,n=1
                case DUP_X1 -> { emit(DUP_X); emit(0x11); }
                // JVM dup_x2 (0x5B) → JCVM dup_x (0x3F) mn=0x12 -- §7.5.17: m=1,n=2
                case DUP_X2 -> { emit(DUP_X); emit(0x12); }
                // JVM swap (0x5F) → JCVM swap_x (0x40) mn=0x11 -- §7.5.84: m=1,n=1
                case SWAP -> { emit(SWAP_X); emit(0x11); }
                default -> {}
            }
        }

        // JVM int arithmetic/bitwise → JCVM short arithmetic/bitwise (without ACC_INT)
        // -- §7.5.68/§7.5.41: int operations narrowed to short equivalents;
        // e.g., iadd (0x60) → sadd (0x41), isub (0x64) → ssub (0x43), etc.
        // With ACC_INT: iadd (0x42), isub (0x44), etc. per §7.5.36-41
        private void translateOperator(OperatorInstruction oi) {
            flushDeferredAload0();
            int jcvmOp = switch (oi.opcode()) {
                // JVM iadd (0x60) → JCVM sadd (0x41) or iadd (0x42) -- §7.5.68/§7.5.36
                case IADD -> supportInt32 ? IADD : SADD;
                // JVM isub (0x64) → JCVM ssub (0x43) or isub (0x44) -- §7.5.82/§7.5.49
                case ISUB -> supportInt32 ? ISUB : SSUB;
                // JVM imul (0x68) → JCVM smul (0x45) or imul (0x46) -- §7.5.78/§7.5.45
                case IMUL -> supportInt32 ? IMUL : SMUL;
                // JVM idiv (0x6C) → JCVM sdiv (0x47) or idiv (0x48) -- §7.5.72/§7.5.39
                case IDIV -> supportInt32 ? IDIV : SDIV;
                // JVM irem (0x70) → JCVM srem (0x49) or irem (0x4A) -- §7.5.80/§7.5.47
                case IREM -> supportInt32 ? IREM : SREM;
                // JVM ineg (0x74) → JCVM sneg (0x4B) or ineg (0x4C) -- §7.5.79/§7.5.46
                case INEG -> supportInt32 ? INEG : SNEG;
                // JVM ishl (0x78) → JCVM sshl (0x4D) or ishl (0x4E) -- §7.5.81/§7.5.48
                case ISHL -> supportInt32 ? ISHL : SSHL;
                // JVM ishr (0x7A) → JCVM sshr (0x4F) or ishr (0x50) -- §7.5.81/§7.5.48
                case ISHR -> supportInt32 ? ISHR : SSHR;
                // JVM iushr (0x7C) → JCVM sushr (0x51) or iushr (0x52) -- §7.5.84/§7.5.51
                case IUSHR -> supportInt32 ? IUSHR : SUSHR;
                // JVM iand (0x7E) → JCVM sand (0x53) or iand (0x54) -- §7.5.69/§7.5.36
                case IAND -> supportInt32 ? IAND : SAND;
                // JVM ior (0x80) → JCVM sor (0x55) or ior (0x56) -- §7.5.79/§7.5.46
                case IOR -> supportInt32 ? IOR : SOR;
                // JVM ixor (0x82) → JCVM sxor (0x57) or ixor (0x58) -- §7.5.85/§7.5.52
                case IXOR -> supportInt32 ? IXOR : SXOR;
                default -> -1;
            };
            if (jcvmOp >= 0) emit(jcvmOp);
        }

        // JVM type conversions → JCVM type conversions
        // -- §7.5.70/§7.5.37: JCVM has s2b (0x5B), s2i (0x5C), i2b (0x5D), i2s (0x5E)
        private void translateConvert(ConvertInstruction ci) {
            flushDeferredAload0();
            switch (ci.opcode()) {
                // JVM i2b (0x91) → JCVM s2b (0x5B) or i2b (0x5D) -- §7.5.70/§7.5.37
                case I2B -> emit(supportInt32 ? I2B : S2B);
                // JVM i2s (0x93) → JCVM i2s (0x5E) -- §7.5.38; nop without ACC_INT (already short)
                case I2S -> { if (supportInt32) emit(I2S); /* else nop — already short */ }
                // JVM i2c (0x92) → nop -- char is unsigned short in JCVM, no conversion needed
                case I2C -> {} // char is short in JCVM
                default -> {}
            }
        }

        // JVM branch instructions → JCVM branch instructions with 1-byte signed offset
        // -- §7.5.22-35: JCVM uses narrow (1-byte) branch offsets by default;
        // wide (_w) variants exist for larger offsets but are not emitted here
        private void translateBranch(BranchInstruction bi) {
            flushDeferredAload0();
            Label target = bi.target();
            // Emit narrow (1-byte offset) branch opcodes — Oracle uses narrow form
            // when the offset fits in [-128, +127]. JCVM methods are small enough
            // that narrow branches are sufficient in practice.
            int jcvmOp = switch (bi.opcode()) {
                // JVM goto/goto_w (0xA7/0xC8) → JCVM goto (0x70) -- §7.5.22
                case GOTO, GOTO_W -> JcvmOpcode.GOTO;
                // JVM ifeq (0x99) → JCVM ifeq (0x60) -- §7.5.23: branch if top == 0
                case IFEQ -> JcvmOpcode.IFEQ;
                // JVM ifne (0x9A) → JCVM ifne (0x61) -- §7.5.26: branch if top != 0
                case IFNE -> JcvmOpcode.IFNE;
                // JVM iflt (0x9B) → JCVM iflt (0x62) -- §7.5.25: branch if top < 0
                case IFLT -> JcvmOpcode.IFLT;
                // JVM ifge (0x9C) → JCVM ifge (0x63) -- §7.5.24: branch if top >= 0
                case IFGE -> JcvmOpcode.IFGE;
                // JVM ifgt (0x9D) → JCVM ifgt (0x64) -- §7.5.24: branch if top > 0
                case IFGT -> JcvmOpcode.IFGT;
                // JVM ifle (0x9E) → JCVM ifle (0x65) -- §7.5.25: branch if top <= 0
                case IFLE -> JcvmOpcode.IFLE;
                // JVM ifnull (0xC6) → JCVM ifnull (0x66) -- §7.5.27: branch if ref is null
                case IFNULL -> JcvmOpcode.IFNULL;
                // JVM ifnonnull (0xC7) → JCVM ifnonnull (0x67) -- §7.5.26: branch if ref not null
                case IFNONNULL -> JcvmOpcode.IFNONNULL;
                // JVM if_acmpeq (0xA5) → JCVM if_acmpeq (0x68) -- §7.5.28: ref equality
                case IF_ACMPEQ -> JcvmOpcode.IF_ACMPEQ;
                // JVM if_acmpne (0xA6) → JCVM if_acmpne (0x69) -- §7.5.28: ref inequality
                case IF_ACMPNE -> JcvmOpcode.IF_ACMPNE;
                // JVM if_icmpeq (0x9F) → JCVM if_scmpeq (0x6A) -- §7.5.29: int→short comparison
                case IF_ICMPEQ -> IF_SCMPEQ;
                // JVM if_icmpne (0xA0) → JCVM if_scmpne (0x6B) -- §7.5.29
                case IF_ICMPNE -> IF_SCMPNE;
                // JVM if_icmplt (0xA1) → JCVM if_scmplt (0x6C) -- §7.5.29
                case IF_ICMPLT -> IF_SCMPLT;
                // JVM if_icmpge (0xA2) → JCVM if_scmpge (0x6D) -- §7.5.29
                case IF_ICMPGE -> IF_SCMPGE;
                // JVM if_icmpgt (0xA3) → JCVM if_scmpgt (0x6E) -- §7.5.29
                case IF_ICMPGT -> IF_SCMPGT;
                // JVM if_icmple (0xA4) → JCVM if_scmple (0x6F) -- §7.5.29
                case IF_ICMPLE -> IF_SCMPLE;
                default -> -1;
            };
            if (jcvmOp < 0) return;

            int instrPos = out.size();
            emit(jcvmOp);
            fixups.add(new Fixup(instrPos, out.size(), target, 1));
            emit(0); // 1-byte placeholder offset
        }

        // JVM return instructions → JCVM return instructions
        // -- §7.5.66/§7.5.5/§7.5.80/§7.5.47: type-specific return opcodes
        private void translateReturn(ReturnInstruction ri) {
            flushDeferredAload0();
            switch (ri.typeKind()) {
                // JVM return (0xB1) → JCVM return (0x7A) -- §7.5.66: void return
                case VOID -> emit(RETURN);
                // JVM areturn (0xB0) → JCVM areturn (0x77) -- §7.5.5: return reference
                case REFERENCE -> emit(ARETURN);
                // JVM ireturn (0xAC) → JCVM sreturn (0x78) or ireturn (0x79)
                // -- §7.5.80/§7.5.47: int narrowed to short without ACC_INT
                case INT -> emit(supportInt32 ? IRETURN : SRETURN);
                // JVM short/char/byte/boolean return → JCVM sreturn (0x78) -- §7.5.80
                default -> emit(SRETURN);
            }
        }

        // JVM getfield/putfield/getstatic/putstatic (0xB4/0xB5/0xB2/0xB3)
        // → JCVM getfield_<t>/putfield_<t>/getstatic_<t>/putstatic_<t>
        // -- §7.5.27-34: split into type-specific variants (_a, _b, _s, _i) based on
        // field descriptor; instance fields use 1-byte CP index, static fields use 2-byte
        private void translateField(FieldInstruction fi) {
            String desc = fi.field().type().stringValue();
            char suffix = fieldTypeSuffix(desc);
            String owner = fi.owner().asInternalName();
            String name = fi.field().name().stringValue();
            boolean isStatic = fi.opcode() == java.lang.classfile.Opcode.GETSTATIC
                    || fi.opcode() == java.lang.classfile.Opcode.PUTSTATIC;

            // _THIS optimization: GETFIELD_x_THIS / PUTFIELD_x_THIS per JCVM spec §7.5.8-9
            // These opcodes implicitly use local[0] as objectref, saving the ALOAD_0 byte.
            if (fi.opcode() == java.lang.classfile.Opcode.GETFIELD && aload0Deferred && !aload0ValuePushed) {
                // Pattern: ALOAD_0 + GETFIELD_x → GETFIELD_x_THIS (0xAD-0xB0) -- §7.5.30
                aload0Deferred = false;
                int op = switch (suffix) {
                    case 'a' -> GETFIELD_A_THIS; case 'b' -> GETFIELD_B_THIS;
                    case 'i' -> GETFIELD_I_THIS; default -> GETFIELD_S_THIS;
                };
                emit(op);
                emitFieldRef(owner, name, desc, false);
                return;
            }
            if (fi.opcode() == java.lang.classfile.Opcode.PUTFIELD && aload0ValuePushed
                    && optimizePutfieldThis) {
                // Pattern: ALOAD_0 + <value_push> + PUTFIELD_x → <value_push> + PUTFIELD_x_THIS
                // (0xB5-0xB8) -- §7.5.63
                // Note: Oracle converters prior to JC 3.0.5 do not apply this optimization,
                // only GETFIELD_x_THIS. We match their behavior per-version for binary compatibility.
                aload0Deferred = false;
                aload0ValuePushed = false;
                int op = switch (suffix) {
                    case 'a' -> PUTFIELD_A_THIS; case 'b' -> PUTFIELD_B_THIS;
                    case 'i' -> PUTFIELD_I_THIS; default -> PUTFIELD_S_THIS;
                };
                emit(op);
                emitFieldRef(owner, name, desc, false);
                return;
            }

            flushDeferredAload0();

            int op;
            switch (fi.opcode()) {
                // JVM getfield (0xB4) → JCVM getfield_<t> (0x83-0x86) -- §7.5.27: 1-byte CP index
                case GETFIELD -> op = switch (suffix) {
                    case 'a' -> GETFIELD_A; case 'b' -> GETFIELD_B;
                    case 'i' -> GETFIELD_I; default -> GETFIELD_S;
                };
                // JVM putfield (0xB5) → JCVM putfield_<t> (0x87-0x8A) -- §7.5.61: 1-byte CP index
                case PUTFIELD -> op = switch (suffix) {
                    case 'a' -> PUTFIELD_A; case 'b' -> PUTFIELD_B;
                    case 'i' -> PUTFIELD_I; default -> PUTFIELD_S;
                };
                // JVM getstatic (0xB2) → JCVM getstatic_<t> (0x7B-0x7E) -- §7.5.33: 2-byte CP index
                case GETSTATIC -> op = switch (suffix) {
                    case 'a' -> GETSTATIC_A; case 'b' -> GETSTATIC_B;
                    case 'i' -> GETSTATIC_I; default -> GETSTATIC_S;
                };
                // JVM putstatic (0xB3) → JCVM putstatic_<t> (0x7F-0x82) -- §7.5.65: 2-byte CP index
                case PUTSTATIC -> op = switch (suffix) {
                    case 'a' -> PUTSTATIC_A; case 'b' -> PUTSTATIC_B;
                    case 'i' -> PUTSTATIC_I; default -> PUTSTATIC_S;
                };
                default -> { return; }
            }

            emit(op);
            emitFieldRef(owner, name, desc, isStatic);
        }

        private void emitFieldRef(String owner, String name, String desc, boolean isStatic) {
            if (resolver != null) {
                int cpIndex = resolver.resolveFieldRef(owner, name, desc, isStatic);
                int refPos = out.size();
                if (!isStatic) {
                    emit(cpIndex);
                    cpReferences.add(new CpReference(refPos, cpIndex, 1));
                } else {
                    emitShort((short) cpIndex);
                    cpReferences.add(new CpReference(refPos, cpIndex, 2));
                }
            } else {
                if (!isStatic) {
                    emit(0);
                } else {
                    emitShort((short) 0);
                }
            }
        }

        // JVM invoke instructions → JCVM invoke instructions
        // -- §7.5.46-49: all use 2-byte CP index referencing method_ref/static_method_ref entries
        private void translateInvoke(InvokeInstruction ii) {
            flushDeferredAload0();
            String owner = ii.owner().asInternalName();
            String name = ii.method().name().stringValue();
            String desc = ii.method().type().stringValue();

            switch (ii.opcode()) {
                // JVM invokevirtual (0xB6) → JCVM invokevirtual (0x8B)
                // -- §7.5.46: 2-byte CP index to virtual_method_ref
                case INVOKEVIRTUAL -> {
                    emit(INVOKEVIRTUAL);
                    emitCpRef(owner, name, desc, ReferenceResolver.InvokeKind.VIRTUAL, 2);
                }
                // JVM invokespecial (0xB7) → JCVM invokespecial (0x8C)
                // -- §7.5.47: 2-byte CP index to static_method_ref (constructors/super calls)
                case INVOKESPECIAL -> {
                    emit(INVOKESPECIAL);
                    emitCpRef(owner, name, desc, ReferenceResolver.InvokeKind.SPECIAL, 2);
                }
                // JVM invokestatic (0xB8) → JCVM invokestatic (0x8D)
                // -- §7.5.48: 2-byte CP index to static_method_ref
                case INVOKESTATIC -> {
                    emit(INVOKESTATIC);
                    emitCpRef(owner, name, desc, ReferenceResolver.InvokeKind.STATIC, 2);
                }
                // JVM invokeinterface (0xB9) → JCVM invokeinterface (0x8E)
                // -- §7.5.49: nargs(1) + 2-byte CP index to interface_method_ref + method_token(1)
                case INVOKEINTERFACE -> {
                    int nargs = countArgs(desc) + 1; // +1 for 'this'
                    emit(INVOKEINTERFACE);
                    emit(nargs);
                    emitCpRef(owner, name, desc, ReferenceResolver.InvokeKind.INTERFACE, 2);
                    emit(0); // method token (unused in JCVM spec, reserved)
                }
                default -> {}
            }
        }

        // JVM new (0xBB) → JCVM new (0x8F) -- §7.5.55: 2-byte CP index to class_ref entry
        private void translateNewObject(NewObjectInstruction ni) {
            flushDeferredAload0();
            emit(NEW);
            emitClassRefOperand(ni.className().asInternalName());
        }

        // JVM newarray (0xBC) → JCVM newarray (0x90)
        // -- §7.5.56: atype byte selects element type (10=boolean, 11=byte, 12=short, 13=int)
        private void translateNewArray(NewPrimitiveArrayInstruction pi) {
            flushDeferredAload0();
            emit(NEWARRAY);
            int atype = switch (pi.typeKind()) {
                case BOOLEAN -> 10; // T_BOOLEAN
                case BYTE -> 11;    // T_BYTE
                case SHORT -> 12;   // T_SHORT
                case INT -> 13;     // T_INT
                default -> 11;
            };
            emit(atype);
        }

        // JVM anewarray (0xBD) → JCVM anewarray (0x91)
        // -- §7.5.4: 2-byte CP index to class_ref for component type
        private void translateANewArray(NewReferenceArrayInstruction ri) {
            flushDeferredAload0();
            emit(ANEWARRAY);
            emitClassRefOperand(ri.componentType().asInternalName());
        }

        // JVM type check instructions → JCVM type check instructions
        // -- §7.5.10/§7.5.44: atype byte (0=class, 1=interface) + 2-byte CP index to class_ref
        private void translateTypeCheck(TypeCheckInstruction ti) {
            flushDeferredAload0();
            switch (ti.opcode()) {
                // JVM checkcast (0xC0) → JCVM checkcast (0x94)
                // -- §7.5.10: atype(1) + 2-byte CP index to class_ref
                case CHECKCAST -> {
                    emit(CHECKCAST);
                    emit(0); // atype: 0 = class
                    emitClassRefOperand(ti.type().asInternalName());
                }
                // JVM instanceof (0xC1) → JCVM instanceof (0x95)
                // -- §7.5.44: atype(1) + 2-byte CP index to class_ref
                case INSTANCEOF -> {
                    emit(INSTANCEOF);
                    emit(0); // atype: 0 = class
                    emitClassRefOperand(ti.type().asInternalName());
                }
                default -> {}
            }
        }

        // JVM tableswitch (0xAA) → JCVM stableswitch (0x73)
        // -- §7.5.83: variable-length; default(2) + low(2) + high(2) + offsets(2 each)
        private void translateTableSwitch(TableSwitchInstruction tsi) {
            flushDeferredAload0();
            int instrPos = out.size();
            emit(STABLESWITCH);
            // JCVM spec §7.5.83: default(2), low(2), high(2), offsets...
            fixups.add(new Fixup(instrPos, out.size(), tsi.defaultTarget(), 2));
            emitShort((short) 0);
            emitShort((short) tsi.lowValue());
            emitShort((short) tsi.highValue());

            for (var sc : tsi.cases()) {
                fixups.add(new Fixup(instrPos, out.size(), sc.target(), 2));
                emitShort((short) 0);
            }
        }

        // JVM lookupswitch (0xAB) → JCVM slookupswitch (0x75)
        // -- §7.5.73: variable-length; default(2) + npairs(2) + match-offset pairs(4 each)
        private void translateLookupSwitch(LookupSwitchInstruction lsi) {
            flushDeferredAload0();
            int instrPos = out.size();
            emit(SLOOKUPSWITCH);
            // JCVM spec §7.5.73: default(2), npairs(2), cases...
            fixups.add(new Fixup(instrPos, out.size(), lsi.defaultTarget(), 2));
            emitShort((short) 0);
            emitShort((short) lsi.cases().size());

            for (var sc : lsi.cases()) {
                emitShort((short) sc.caseValue());
                fixups.add(new Fixup(instrPos, out.size(), sc.target(), 2));
                emitShort((short) 0);
            }
        }

        // ── _THIS optimization helpers ──

        /**
         * Flushes any deferred ALOAD_0 by emitting it. If we're in VALUE_PUSHED
         * state (ALOAD_0 deferred + value push already emitted), we retroactively
         * insert ALOAD_0 before the value push and adjust positions.
         */
        private void flushDeferredAload0() {
            if (aload0Deferred) {
                if (aload0ValuePushed) {
                    // Insert ALOAD_0 before the value push (retroactive insertion)
                    byte[] current = out.toByteArray();
                    byte[] before = java.util.Arrays.copyOfRange(current, 0, valuePushCheckpoint);
                    byte[] after = java.util.Arrays.copyOfRange(current, valuePushCheckpoint, current.length);
                    out.reset();
                    out.writeBytes(before);
                    out.write(ALOAD_0 & 0xFF);
                    out.writeBytes(after);
                    // No label, fixup, or cpReference adjustments needed because
                    // simple pushes (sconst, bspush, sload, aload) don't add any
                    aload0ValuePushed = false;
                } else {
                    emit(ALOAD_0);
                }
                aload0Deferred = false;
            }
        }

        /**
         * Called for "simple push" instructions (constants, loads) that might
         * be followed by PUTFIELD. Instead of flushing the deferred ALOAD_0,
         * we enter VALUE_PUSHED state and defer the decision until we see
         * what comes next.
         */
        private void enterValuePushedState() {
            aload0ValuePushed = true;
            valuePushCheckpoint = out.size();
        }

        // ── Helpers ──

        private void emitCpRef(String owner, String name, String desc,
                               ReferenceResolver.InvokeKind kind, int size) {
            if (resolver != null) {
                int cpIndex = resolver.resolveMethodRef(owner, name, desc, kind);
                int refPos = out.size();
                emitShort((short) cpIndex);
                cpReferences.add(new CpReference(refPos, cpIndex, size));
            } else {
                emitShort((short) 0);
            }
        }

        private void emitClassRefOperand(String className) {
            if (resolver != null) {
                int cpIndex = resolver.resolveClassRef(className);
                int refPos = out.size();
                emitShort((short) cpIndex);
                cpReferences.add(new CpReference(refPos, cpIndex, 2));
            } else {
                emitShort((short) 0);
            }
        }

        private void emit(int b) {
            out.write(b & 0xFF);
        }

        private void emitShort(short v) {
            out.write((v >> 8) & 0xFF);
            out.write(v & 0xFF);
        }

        private void emitInt(int v) {
            out.write((v >> 24) & 0xFF);
            out.write((v >> 16) & 0xFF);
            out.write((v >> 8) & 0xFF);
            out.write(v & 0xFF);
        }

        private void resolveFixups() {
            byte[] bytes = out.toByteArray();
            for (Fixup f : fixups) {
                Integer targetPos = labelPositions.get(f.target);
                if (targetPos == null) continue;
                int offset = targetPos - f.instrPos;
                if (f.offsetSize == 1) {
                    // Narrow branch: 1-byte signed offset [-128, +127]
                    if (offset < -128 || offset > 127) {
                        throw new IllegalStateException(
                                "Branch offset " + offset + " exceeds 1-byte range; "
                                + "method too large for narrow branches");
                    }
                    bytes[f.offsetPos] = (byte) offset;
                } else {
                    // Wide branch or switch: 2-byte signed offset
                    bytes[f.offsetPos] = (byte) (offset >> 8);
                    bytes[f.offsetPos + 1] = (byte) offset;
                }
            }
            out.reset();
            out.writeBytes(bytes);
        }

        /**
         * Pre-registers catch type ClassRefs in the CP before bytecode translation.
         * This matches Oracle's ordering where exception handler types are resolved
         * before processing method bytecodes, resulting in catch type ClassRef entries
         * appearing at earlier CP indices.
         */
        private void preRegisterCatchTypes(CodeModel code) {
            if (resolver == null) return;
            for (var handler : code.exceptionHandlers()) {
                if (handler.catchType().isPresent()) {
                    String catchClass = handler.catchType().orElseThrow().asInternalName();
                    resolver.resolveClassRef(catchClass);
                }
            }
        }

        private void buildExceptionHandlers(CodeModel code) {
            if (!(code instanceof CodeAttribute ca)) return;
            for (var handler : code.exceptionHandlers()) {
                Integer start = labelPositions.get(handler.tryStart());
                Integer end = labelPositions.get(handler.tryEnd());
                Integer handlerPos = labelPositions.get(handler.handler());
                if (start == null || end == null || handlerPos == null) continue;

                int catchType = 0; // 0 = finally/catch-all
                if (resolver != null && handler.catchType().isPresent()) {
                    String catchClass = handler.catchType().orElseThrow().asInternalName();
                    catchType = resolver.resolveClassRef(catchClass);
                }
                handlers.add(new TranslatedMethod.JcvmExceptionHandler(
                        start, end, handlerPos, catchType));
            }
        }

        static int countArgs(String descriptor) {
            int count = 0;
            int i = 1; // skip '('
            while (i < descriptor.length() && descriptor.charAt(i) != ')') {
                char c = descriptor.charAt(i);
                if (c == 'L') {
                    count++;
                    i = descriptor.indexOf(';', i) + 1;
                } else if (c == '[') {
                    i++;
                } else {
                    count++;
                    i++;
                }
            }
            return count;
        }

        private record Fixup(int instrPos, int offsetPos, Label target, int offsetSize) {}
    }
}
