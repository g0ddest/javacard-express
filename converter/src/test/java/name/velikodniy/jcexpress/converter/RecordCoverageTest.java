package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.cap.AppletComponent;
import name.velikodniy.jcexpress.converter.cap.ClassComponent;
import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.ExceptionHandlerInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.resolve.CpReference;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests equals/hashCode/toString of all record types in the converter module.
 * Records with byte[] fields need special attention since arrays don't have
 * value-based equals by default.
 */
class RecordCoverageTest {

    // ── Violation ──

    @Nested
    class ViolationRecordTest {
        @Test
        void equals_identicalInstances() {
            var a = new Violation("A", "ctx", 5, "msg");
            var b = new Violation("A", "ctx", 5, "msg");
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void equals_differentBci() {
            var a = new Violation("A", "ctx", 5, "msg");
            var b = new Violation("A", "ctx", 10, "msg");
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void toString_withBci() {
            var v = new Violation("com/ex/A", "method()V", 42, "forbidden");
            assertThat(v.toString()).contains("com/ex/A", "method()V", "bci 42", "forbidden");
        }

        @Test
        void toString_withoutBci() {
            var v = new Violation("com/ex/A", "field x", "not supported");
            assertThat(v.toString()).contains("com/ex/A", "field x", "not supported")
                    .doesNotContain("bci");
        }

        @Test
        void convenienceConstructor_setsBciToMinus1() {
            var v = new Violation("A", "ctx", "msg");
            assertThat(v.bci()).isEqualTo(-1);
        }
    }

    // ── CpReference ──

    @Nested
    class CpReferenceRecordTest {
        @Test
        void equals_identicalInstances() {
            var a = new CpReference(10, 5, 2);
            var b = new CpReference(10, 5, 2);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void equals_differentValues() {
            var a = new CpReference(10, 5, 2);
            var b = new CpReference(10, 5, 1);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void accessors() {
            var r = new CpReference(100, 42, 2);
            assertThat(r.bytecodeOffset()).isEqualTo(100);
            assertThat(r.cpIndex()).isEqualTo(42);
            assertThat(r.indexSize()).isEqualTo(2);
        }

        @Test
        void toString_containsValues() {
            var r = new CpReference(10, 5, 2);
            assertThat(r.toString()).contains("10", "5", "2");
        }
    }

    // ── ExceptionHandlerInfo ──

    @Nested
    class ExceptionHandlerInfoRecordTest {
        @Test
        void equals_identicalInstances() {
            var a = new ExceptionHandlerInfo(0, 10, 20, "java/lang/Exception");
            var b = new ExceptionHandlerInfo(0, 10, 20, "java/lang/Exception");
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void equals_differentCatchType() {
            var a = new ExceptionHandlerInfo(0, 10, 20, "java/lang/Exception");
            var b = new ExceptionHandlerInfo(0, 10, 20, null); // finally block
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void nullCatchType_forFinallyBlock() {
            var h = new ExceptionHandlerInfo(0, 10, 20, null);
            assertThat(h.catchType()).isNull();
        }

        @Test
        void accessors() {
            var h = new ExceptionHandlerInfo(5, 15, 25, "pkg/Ex");
            assertThat(h.startPc()).isEqualTo(5);
            assertThat(h.endPc()).isEqualTo(15);
            assertThat(h.handlerPc()).isEqualTo(25);
            assertThat(h.catchType()).isEqualTo("pkg/Ex");
        }
    }

    // ── FieldInfo ──

    @Nested
    class FieldInfoRecordTest {
        @Test
        void isStatic_withStaticFlag() {
            var f = new FieldInfo("x", "S", 0x0008, null);
            assertThat(f.isStatic()).isTrue();
        }

        @Test
        void isStatic_withoutStaticFlag() {
            var f = new FieldInfo("x", "S", 0x0001, null);
            assertThat(f.isStatic()).isFalse();
        }

        @Test
        void isFinal_withFinalFlag() {
            var f = new FieldInfo("X", "I", 0x0010, null);
            assertThat(f.isFinal()).isTrue();
        }

        @Test
        void isFinal_withoutFinalFlag() {
            var f = new FieldInfo("x", "I", 0x0000, null);
            assertThat(f.isFinal()).isFalse();
        }

        @Test
        void isCompileTimeConstant_staticFinalPrimitiveWithValue() {
            var f = new FieldInfo("CONST", "B", 0x0018, (int) 42);
            assertThat(f.isCompileTimeConstant()).isTrue();
        }

        @Test
        void isCompileTimeConstant_staticFinalWithoutValue() {
            var f = new FieldInfo("CONST", "B", 0x0018, null);
            assertThat(f.isCompileTimeConstant()).isFalse();
        }

        @Test
        void isCompileTimeConstant_staticFinalObjectRef() {
            var f = new FieldInfo("REF", "Ljava/lang/Object;", 0x0018, "value");
            assertThat(f.isCompileTimeConstant()).isFalse();
        }

        @Test
        void isCompileTimeConstant_staticFinalArrayRef() {
            var f = new FieldInfo("ARR", "[B", 0x0018, null);
            assertThat(f.isCompileTimeConstant()).isFalse();
        }

        @Test
        void isCompileTimeConstant_nonStatic() {
            var f = new FieldInfo("x", "I", 0x0010, (int) 42); // final but not static
            assertThat(f.isCompileTimeConstant()).isFalse();
        }

        @Test
        void equals_identicalInstances() {
            var a = new FieldInfo("name", "S", 0x0008, null);
            var b = new FieldInfo("name", "S", 0x0008, null);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void equals_differentName() {
            var a = new FieldInfo("a", "S", 0, null);
            var b = new FieldInfo("b", "S", 0, null);
            assertThat(a).isNotEqualTo(b);
        }
    }

    // ── MethodInfo ──

    @Nested
    class MethodInfoRecordTest {
        @Test
        void isAbstract() {
            var m = new MethodInfo("m", "()V", 0x0400, 0, 0, new byte[0], List.of());
            assertThat(m.isAbstract()).isTrue();
        }

        @Test
        void isStatic() {
            var m = new MethodInfo("m", "()V", 0x0008, 0, 0, new byte[0], List.of());
            assertThat(m.isStatic()).isTrue();
        }

        @Test
        void isNative() {
            var m = new MethodInfo("m", "()V", 0x0100, 0, 0, new byte[0], List.of());
            assertThat(m.isNative()).isTrue();
        }

        @Test
        void isConstructor() {
            var m = new MethodInfo("<init>", "()V", 0, 0, 0, new byte[0], List.of());
            assertThat(m.isConstructor()).isTrue();
        }

        @Test
        void isConstructor_regularMethod() {
            var m = new MethodInfo("init", "()V", 0, 0, 0, new byte[0], List.of());
            assertThat(m.isConstructor()).isFalse();
        }

        @Test
        void isStaticInitializer() {
            var m = new MethodInfo("<clinit>", "()V", 0x0008, 0, 0, new byte[0], List.of());
            assertThat(m.isStaticInitializer()).isTrue();
        }

        @Test
        void isStaticInitializer_regularMethod() {
            var m = new MethodInfo("clinit", "()V", 0, 0, 0, new byte[0], List.of());
            assertThat(m.isStaticInitializer()).isFalse();
        }

        @Test
        void noFlags_allFalse() {
            var m = new MethodInfo("m", "()V", 0, 0, 0, new byte[0], List.of());
            assertThat(m.isAbstract()).isFalse();
            assertThat(m.isStatic()).isFalse();
            assertThat(m.isNative()).isFalse();
        }

        @Test
        void equals_withByteArrayField() {
            // Custom equals uses Arrays.equals for content-based comparison
            var a = new MethodInfo("m", "()V", 0, 1, 1, new byte[]{1, 2}, List.of());
            var b = new MethodInfo("m", "()V", 0, 1, 1, new byte[]{1, 2}, List.of());
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_sameByteArrayInstance() {
            byte[] code = {1, 2, 3};
            var a = new MethodInfo("m", "()V", 0, 1, 1, code, List.of());
            var b = new MethodInfo("m", "()V", 0, 1, 1, code, List.of());
            assertThat(a).isEqualTo(b);
        }
    }

    // ── ClassInfo ──

    @Nested
    class ClassInfoRecordTest {
        @Test
        void isInterface() {
            var ci = new ClassInfo("pkg/I", null, List.of(), 0x0200, List.of(), List.of());
            assertThat(ci.isInterface()).isTrue();
        }

        @Test
        void isInterface_concreteClass() {
            var ci = new ClassInfo("pkg/C", "java/lang/Object", List.of(), 0x0001, List.of(), List.of());
            assertThat(ci.isInterface()).isFalse();
        }

        @Test
        void isAbstract() {
            var ci = new ClassInfo("pkg/A", "java/lang/Object", List.of(), 0x0400, List.of(), List.of());
            assertThat(ci.isAbstract()).isTrue();
        }

        @Test
        void simpleName() {
            var ci = new ClassInfo("com/example/MyApplet", "java/lang/Object", List.of(), 0, List.of(), List.of());
            assertThat(ci.simpleName()).isEqualTo("MyApplet");
        }

        @Test
        void simpleName_noPackage() {
            var ci = new ClassInfo("MyClass", null, List.of(), 0, List.of(), List.of());
            assertThat(ci.simpleName()).isEqualTo("MyClass");
        }

        @Test
        void packageName() {
            var ci = new ClassInfo("com/example/MyApplet", "java/lang/Object", List.of(), 0, List.of(), List.of());
            assertThat(ci.packageName()).isEqualTo("com/example");
        }

        @Test
        void packageName_defaultPackage() {
            var ci = new ClassInfo("MyClass", null, List.of(), 0, List.of(), List.of());
            assertThat(ci.packageName()).isEmpty();
        }
    }

    // ── PackageInfo ──

    @Nested
    class PackageInfoRecordTest {
        @Test
        void internalName_convertsDotToSlash() {
            var pi = new PackageInfo("com.example", List.of());
            assertThat(pi.internalName()).isEqualTo("com/example");
        }

        @Test
        void internalName_singleSegment() {
            var pi = new PackageInfo("example", List.of());
            assertThat(pi.internalName()).isEqualTo("example");
        }

        @Test
        void equals_identical() {
            var a = new PackageInfo("pkg", List.of());
            var b = new PackageInfo("pkg", List.of());
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void equals_different() {
            var a = new PackageInfo("pkg1", List.of());
            var b = new PackageInfo("pkg2", List.of());
            assertThat(a).isNotEqualTo(b);
        }
    }

    // ── ConverterResult ──

    @Nested
    class ConverterResultRecordTest {
        @Test
        void accessors() {
            byte[] cap = {1, 2, 3};
            byte[] exp = {4, 5};
            var r = new ConverterResult(cap, exp, List.of("warn1"), 3);

            assertThat(r.capFile()).isEqualTo(cap);
            assertThat(r.exportFile()).isEqualTo(exp);
            assertThat(r.warnings()).containsExactly("warn1");
            assertThat(r.capSize()).isEqualTo(3);
        }

        @Test
        void equals_sameContentArrays() {
            var a = new ConverterResult(new byte[]{1, 2, 3}, new byte[]{4, 5}, List.of("w"), 3);
            var b = new ConverterResult(new byte[]{1, 2, 3}, new byte[]{4, 5}, List.of("w"), 3);
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            var a = new ConverterResult(new byte[]{1}, new byte[]{2}, List.of(), 1);
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentCapFile() {
            var a = new ConverterResult(new byte[]{1, 2}, new byte[]{4}, List.of(), 2);
            var b = new ConverterResult(new byte[]{1, 3}, new byte[]{4}, List.of(), 2);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentExportFile() {
            var a = new ConverterResult(new byte[]{1}, new byte[]{4}, List.of(), 1);
            var b = new ConverterResult(new byte[]{1}, new byte[]{5}, List.of(), 1);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentWarnings() {
            var a = new ConverterResult(new byte[]{1}, new byte[]{2}, List.of("a"), 1);
            var b = new ConverterResult(new byte[]{1}, new byte[]{2}, List.of("b"), 1);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentCapSize() {
            var a = new ConverterResult(new byte[]{1}, new byte[]{2}, List.of(), 1);
            var b = new ConverterResult(new byte[]{1}, new byte[]{2}, List.of(), 99);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_null() {
            var a = new ConverterResult(new byte[]{1}, new byte[]{2}, List.of(), 1);
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        void equals_differentType() {
            var a = new ConverterResult(new byte[]{1}, new byte[]{2}, List.of(), 1);
            assertThat(a).isNotEqualTo("not a ConverterResult");
        }

        @Test
        void hashCode_consistency() {
            var a = new ConverterResult(new byte[]{1, 2, 3}, new byte[]{4, 5}, List.of("w"), 3);
            var b = new ConverterResult(new byte[]{1, 2, 3}, new byte[]{4, 5}, List.of("w"), 3);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void hashCode_differentInstances() {
            var a = new ConverterResult(new byte[]{1}, new byte[]{2}, List.of(), 1);
            var b = new ConverterResult(new byte[]{9}, new byte[]{8}, List.of(), 9);
            // Not guaranteed to differ, but practically should for different content
            assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
        }

        @Test
        void toString_containsHexRepresentation() {
            var r = new ConverterResult(new byte[]{(byte) 0xCA, (byte) 0xFE},
                    new byte[]{(byte) 0xBE, (byte) 0xEF}, List.of("test-warning"), 2);
            String s = r.toString();
            assertThat(s).contains("cafe"); // hex of CA FE
            assertThat(s).contains("beef"); // hex of BE EF
            assertThat(s).contains("test-warning");
            assertThat(s).contains("capSize=2");
        }

        @Test
        void toString_containsInfo() {
            var r = new ConverterResult(new byte[10], new byte[5], List.of(), 10);
            assertThat(r.toString()).isNotBlank();
        }
    }

    // ── ImportedPackage ──

    @Nested
    class ImportedPackageRecordTest {
        @Test
        void accessors() {
            byte[] aid = {(byte) 0xA0, 0x00};
            ExportFile ef = new ExportFile("pkg", aid, 1, 0, List.of());
            var ip = new ImportedPackage(0, aid, 1, 0, ef);

            assertThat(ip.token()).isZero();
            assertThat(ip.aid()).isEqualTo(aid);
            assertThat(ip.majorVersion()).isEqualTo(1);
            assertThat(ip.minorVersion()).isZero();
            assertThat(ip.exportFile()).isSameAs(ef);
        }

        @Test
        void equals_sameAidInstance() {
            byte[] aid = {(byte) 0xA0};
            ExportFile ef = new ExportFile("p", aid, 1, 0, List.of());
            var a = new ImportedPackage(0, aid, 1, 0, ef);
            var b = new ImportedPackage(0, aid, 1, 0, ef);
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_differentAidInstances() {
            // Custom equals uses Arrays.equals for content-based comparison
            byte[] aid1 = {(byte) 0xA0};
            byte[] aid2 = {(byte) 0xA0};
            ExportFile ef = new ExportFile("p", aid1, 1, 0, List.of());
            var a = new ImportedPackage(0, aid1, 1, 0, ef);
            var b = new ImportedPackage(0, aid2, 1, 0, ef);
            assertThat(a).isEqualTo(b);
        }
    }

    // ── ExportFile nested records ──

    @Nested
    class ExportFileRecordTest {
        @Test
        void classExport_accessors() {
            var ce = new ExportFile.ClassExport("Applet", 3, 0x0001, List.of(), List.of());
            assertThat(ce.name()).isEqualTo("Applet");
            assertThat(ce.token()).isEqualTo(3);
            assertThat(ce.accessFlags()).isEqualTo(0x0001);
        }

        @Test
        void methodExport_accessors() {
            var me = new ExportFile.MethodExport("process", "(LAPDU;)V", 7, 0x0001);
            assertThat(me.name()).isEqualTo("process");
            assertThat(me.descriptor()).isEqualTo("(LAPDU;)V");
            assertThat(me.token()).isEqualTo(7);
            assertThat(me.accessFlags()).isEqualTo(0x0001);
        }

        @Test
        void fieldExport_accessors() {
            var fe = new ExportFile.FieldExport("SW_OK", "S", 0, 0x0019);
            assertThat(fe.name()).isEqualTo("SW_OK");
            assertThat(fe.descriptor()).isEqualTo("S");
            assertThat(fe.token()).isZero();
            assertThat(fe.accessFlags()).isEqualTo(0x0019);
        }

        @Test
        void classExport_equals() {
            var a = new ExportFile.ClassExport("X", 0, 1, List.of(), List.of());
            var b = new ExportFile.ClassExport("X", 0, 1, List.of(), List.of());
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void methodExport_equals() {
            var a = new ExportFile.MethodExport("m", "()V", 0, 1);
            var b = new ExportFile.MethodExport("m", "()V", 0, 1);
            assertThat(a).isEqualTo(b);
        }

        @Test
        void fieldExport_equals() {
            var a = new ExportFile.FieldExport("f", "S", 0, 1);
            var b = new ExportFile.FieldExport("f", "S", 0, 1);
            assertThat(a).isEqualTo(b);
        }
    }

    // ── TokenMap nested records ──

    @Nested
    class TokenMapRecordTest {
        @Test
        void methodEntry_equals() {
            var a = new TokenMap.MethodEntry("m", "()V", 0);
            var b = new TokenMap.MethodEntry("m", "()V", 0);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void methodEntry_notEqual() {
            var a = new TokenMap.MethodEntry("m", "()V", 0);
            var b = new TokenMap.MethodEntry("m", "()V", 1);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void fieldEntry_equals() {
            var a = new TokenMap.FieldEntry("f", "S", 0);
            var b = new TokenMap.FieldEntry("f", "S", 0);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void fieldEntry_notEqual() {
            var a = new TokenMap.FieldEntry("f", "S", 0);
            var b = new TokenMap.FieldEntry("g", "S", 0);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void classEntry_toString() {
            var ce = new TokenMap.ClassEntry("pkg/A", 0, List.of(), List.of(), List.of(), List.of());
            assertThat(ce.toString()).contains("pkg/A");
        }

        @Test
        void tokenMap_toString() {
            var tm = new TokenMap("com.example", List.of());
            assertThat(tm.toString()).contains("com.example");
        }
    }

    // ── TranslatedMethod ──

    @Nested
    class TranslatedMethodRecordTest {
        @Test
        void equals_sameContent() {
            var a = new TranslatedMethod(new byte[]{1, 2}, 3, 4, 5, List.of(), false, List.of());
            var b = new TranslatedMethod(new byte[]{1, 2}, 3, 4, 5, List.of(), false, List.of());
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            var a = new TranslatedMethod(new byte[]{1}, 1, 1, 1, List.of(), false, List.of());
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentBytecode() {
            var a = new TranslatedMethod(new byte[]{1, 2}, 3, 4, 5, List.of(), false, List.of());
            var b = new TranslatedMethod(new byte[]{1, 9}, 3, 4, 5, List.of(), false, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentMaxStack() {
            var a = new TranslatedMethod(new byte[]{1}, 3, 4, 5, List.of(), false, List.of());
            var b = new TranslatedMethod(new byte[]{1}, 7, 4, 5, List.of(), false, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentMaxLocals() {
            var a = new TranslatedMethod(new byte[]{1}, 3, 4, 5, List.of(), false, List.of());
            var b = new TranslatedMethod(new byte[]{1}, 3, 9, 5, List.of(), false, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentNargs() {
            var a = new TranslatedMethod(new byte[]{1}, 3, 4, 5, List.of(), false, List.of());
            var b = new TranslatedMethod(new byte[]{1}, 3, 4, 9, List.of(), false, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentIsExtended() {
            var a = new TranslatedMethod(new byte[]{1}, 3, 4, 5, List.of(), false, List.of());
            var b = new TranslatedMethod(new byte[]{1}, 3, 4, 5, List.of(), true, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_null() {
            var a = new TranslatedMethod(new byte[]{1}, 3, 4, 5, List.of(), false, List.of());
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        void equals_differentType() {
            var a = new TranslatedMethod(new byte[]{1}, 3, 4, 5, List.of(), false, List.of());
            assertThat(a).isNotEqualTo("not a TranslatedMethod");
        }

        @Test
        void hashCode_consistency() {
            var a = new TranslatedMethod(new byte[]{1, 2}, 3, 4, 5, List.of(), false, List.of());
            var b = new TranslatedMethod(new byte[]{1, 2}, 3, 4, 5, List.of(), false, List.of());
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_containsHexAndCounts() {
            var handlers = List.of(new TranslatedMethod.JcvmExceptionHandler(0, 10, 20, 0));
            var cpRefs = List.of(new CpReference(0, 1, 2));
            var tm = new TranslatedMethod(new byte[]{(byte) 0xAB, (byte) 0xCD}, 5, 3, 2,
                    handlers, true, cpRefs);
            String s = tm.toString();
            assertThat(s).contains("abcd");
            assertThat(s).contains("maxStack=5");
            assertThat(s).contains("maxLocals=3");
            assertThat(s).contains("nargs=2");
            assertThat(s).contains("isExtended=true");
            assertThat(s).contains("handlers=1");
            assertThat(s).contains("cpRefs=1");
        }

        @Test
        void emptyConstant_hasDefaults() {
            var empty = TranslatedMethod.EMPTY;
            assertThat(empty.bytecode()).isEmpty();
            assertThat(empty.maxStack()).isZero();
            assertThat(empty.maxLocals()).isZero();
            assertThat(empty.nargs()).isZero();
            assertThat(empty.exceptionHandlers()).isEmpty();
            assertThat(empty.isExtended()).isFalse();
            assertThat(empty.cpReferences()).isEmpty();
        }
    }

    // ── JcvmExceptionHandler ──

    @Nested
    class JcvmExceptionHandlerRecordTest {
        @Test
        void equals_identicalInstances() {
            var a = new TranslatedMethod.JcvmExceptionHandler(0, 10, 20, 5);
            var b = new TranslatedMethod.JcvmExceptionHandler(0, 10, 20, 5);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void equals_differentValues() {
            var a = new TranslatedMethod.JcvmExceptionHandler(0, 10, 20, 5);
            var b = new TranslatedMethod.JcvmExceptionHandler(0, 10, 20, 0);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void accessors() {
            var h = new TranslatedMethod.JcvmExceptionHandler(1, 11, 21, 3);
            assertThat(h.startOffset()).isEqualTo(1);
            assertThat(h.endOffset()).isEqualTo(11);
            assertThat(h.handlerOffset()).isEqualTo(21);
            assertThat(h.catchTypeIndex()).isEqualTo(3);
        }
    }

    // ── AppletEntry ──

    @Nested
    class AppletEntryRecordTest {
        @Test
        void equals_sameContent() {
            var a = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0, 0x01}, 100);
            var b = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0, 0x01}, 100);
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            var a = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0}, 50);
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentAid() {
            var a = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0, 0x01}, 100);
            var b = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0, 0x02}, 100);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentOffset() {
            var a = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0}, 100);
            var b = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0}, 200);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_null() {
            var a = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0}, 100);
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        void equals_differentType() {
            var a = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0}, 100);
            assertThat(a).isNotEqualTo("not an AppletEntry");
        }

        @Test
        void hashCode_consistency() {
            var a = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0, 0x01}, 100);
            var b = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0, 0x01}, 100);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_containsHex() {
            var e = new AppletComponent.AppletEntry(new byte[]{(byte) 0xA0, (byte) 0xFF}, 42);
            String s = e.toString();
            assertThat(s).contains("a0ff");
            assertThat(s).contains("42");
        }
    }

    // ── AppletComponent.generate ──

    @Nested
    class AppletComponentGenerateTest {
        @Test
        void generateSingleApplet() {
            byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01, 0x01, 0x01, 0x01};
            var entry = new AppletComponent.AppletEntry(aid, 42);
            byte[] result = AppletComponent.generate(List.of(entry));

            // Tag should be 3
            assertThat(result[0] & 0xFF).isEqualTo(3);
            // Size is 2-byte big-endian
            int size = ((result[1] & 0xFF) << 8) | (result[2] & 0xFF);
            assertThat(size).isEqualTo(result.length - 3); // total minus tag(1) + size(2)

            // count should be 1
            assertThat(result[3] & 0xFF).isEqualTo(1);
            // AID length should be 9
            assertThat(result[4] & 0xFF).isEqualTo(aid.length);
        }

        @Test
        void generateMultipleApplets() {
            byte[] aid1 = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x01};
            byte[] aid2 = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x02};
            var entries = List.of(
                    new AppletComponent.AppletEntry(aid1, 10),
                    new AppletComponent.AppletEntry(aid2, 20)
            );
            byte[] result = AppletComponent.generate(entries);

            // Tag = 3
            assertThat(result[0] & 0xFF).isEqualTo(3);
            // count = 2
            assertThat(result[3] & 0xFF).isEqualTo(2);
        }

        @Test
        void generateEmptyList() {
            byte[] result = AppletComponent.generate(List.of());
            // Tag = 3
            assertThat(result[0] & 0xFF).isEqualTo(3);
            // count = 0
            assertThat(result[3] & 0xFF).isEqualTo(0);
        }
    }

    // ── ClassResult ──

    @Nested
    class ClassResultRecordTest {
        @Test
        void equals_sameContent() {
            var a = new ClassComponent.ClassResult(new byte[]{1, 2, 3}, new int[]{0, 5});
            var b = new ClassComponent.ClassResult(new byte[]{1, 2, 3}, new int[]{0, 5});
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            var a = new ClassComponent.ClassResult(new byte[]{1}, new int[]{0});
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentBytes() {
            var a = new ClassComponent.ClassResult(new byte[]{1, 2}, new int[]{0});
            var b = new ClassComponent.ClassResult(new byte[]{1, 3}, new int[]{0});
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentOffsets() {
            var a = new ClassComponent.ClassResult(new byte[]{1}, new int[]{0, 5});
            var b = new ClassComponent.ClassResult(new byte[]{1}, new int[]{0, 9});
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_null() {
            var a = new ClassComponent.ClassResult(new byte[]{1}, new int[]{0});
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        void equals_differentType() {
            var a = new ClassComponent.ClassResult(new byte[]{1}, new int[]{0});
            assertThat(a).isNotEqualTo("not a ClassResult");
        }

        @Test
        void hashCode_consistency() {
            var a = new ClassComponent.ClassResult(new byte[]{1, 2, 3}, new int[]{0, 5});
            var b = new ClassComponent.ClassResult(new byte[]{1, 2, 3}, new int[]{0, 5});
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_containsHexAndOffsets() {
            var r = new ClassComponent.ClassResult(new byte[]{(byte) 0xDE, (byte) 0xAD}, new int[]{0, 10});
            String s = r.toString();
            assertThat(s).contains("dead");
            assertThat(s).contains("0");
            assertThat(s).contains("10");
        }
    }

    // ── ImportedPackage additional tests ──

    @Nested
    class ImportedPackageAdditionalTest {
        @Test
        void equals_null() {
            byte[] aid = {(byte) 0xA0};
            ExportFile ef = new ExportFile("p", aid, 1, 0, List.of());
            var a = new ImportedPackage(0, aid, 1, 0, ef);
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        void equals_differentType() {
            byte[] aid = {(byte) 0xA0};
            ExportFile ef = new ExportFile("p", aid, 1, 0, List.of());
            var a = new ImportedPackage(0, aid, 1, 0, ef);
            assertThat(a).isNotEqualTo("not an ImportedPackage");
        }

        @Test
        void equals_differentToken() {
            byte[] aid = {(byte) 0xA0};
            ExportFile ef = new ExportFile("p", aid, 1, 0, List.of());
            var a = new ImportedPackage(0, aid, 1, 0, ef);
            var b = new ImportedPackage(1, aid, 1, 0, ef);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentMajorVersion() {
            byte[] aid = {(byte) 0xA0};
            ExportFile ef = new ExportFile("p", aid, 1, 0, List.of());
            var a = new ImportedPackage(0, aid, 1, 0, ef);
            var b = new ImportedPackage(0, aid, 2, 0, ef);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentMinorVersion() {
            byte[] aid = {(byte) 0xA0};
            ExportFile ef = new ExportFile("p", aid, 1, 0, List.of());
            var a = new ImportedPackage(0, aid, 1, 0, ef);
            var b = new ImportedPackage(0, aid, 1, 5, ef);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void hashCode_consistency() {
            byte[] aid1 = {(byte) 0xA0};
            byte[] aid2 = {(byte) 0xA0};
            ExportFile ef = new ExportFile("p", aid1, 1, 0, List.of());
            var a = new ImportedPackage(0, aid1, 1, 0, ef);
            var b = new ImportedPackage(0, aid2, 1, 0, ef);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_containsFields() {
            byte[] aid = {(byte) 0xA0, (byte) 0xFF};
            ExportFile ef = new ExportFile("p", aid, 1, 2, List.of());
            var ip = new ImportedPackage(3, aid, 1, 2, ef);
            String s = ip.toString();
            assertThat(s).contains("token=3");
            assertThat(s).contains("a0ff");
            assertThat(s).contains("majorVersion=1");
            assertThat(s).contains("minorVersion=2");
        }
    }

    // ── ExportFile additional tests ──

    @Nested
    class ExportFileAdditionalTest {
        @Test
        void equals_sameContent() {
            byte[] aid1 = {(byte) 0xA0};
            byte[] aid2 = {(byte) 0xA0};
            var a = new ExportFile("pkg", aid1, 1, 0, List.of());
            var b = new ExportFile("pkg", aid2, 1, 0, List.of());
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            var a = new ExportFile("pkg", new byte[]{1}, 1, 0, List.of());
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_null() {
            var a = new ExportFile("pkg", new byte[]{1}, 1, 0, List.of());
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        void equals_differentType() {
            var a = new ExportFile("pkg", new byte[]{1}, 1, 0, List.of());
            assertThat(a).isNotEqualTo("not an ExportFile");
        }

        @Test
        void equals_differentPackageName() {
            byte[] aid = {1};
            var a = new ExportFile("pkg1", aid, 1, 0, List.of());
            var b = new ExportFile("pkg2", aid, 1, 0, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentAid() {
            var a = new ExportFile("pkg", new byte[]{1}, 1, 0, List.of());
            var b = new ExportFile("pkg", new byte[]{2}, 1, 0, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentMajorVersion() {
            byte[] aid = {1};
            var a = new ExportFile("pkg", aid, 1, 0, List.of());
            var b = new ExportFile("pkg", aid, 2, 0, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentMinorVersion() {
            byte[] aid = {1};
            var a = new ExportFile("pkg", aid, 1, 0, List.of());
            var b = new ExportFile("pkg", aid, 1, 5, List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void hashCode_consistency() {
            var a = new ExportFile("pkg", new byte[]{1, 2}, 1, 0, List.of());
            var b = new ExportFile("pkg", new byte[]{1, 2}, 1, 0, List.of());
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_containsFields() {
            var ef = new ExportFile("test.pkg", new byte[]{(byte) 0xCA, (byte) 0xFE}, 3, 1, List.of());
            String s = ef.toString();
            assertThat(s).contains("test.pkg");
            assertThat(s).contains("cafe");
            assertThat(s).contains("majorVersion=3");
            assertThat(s).contains("minorVersion=1");
        }
    }

    // ── MethodInfo additional tests ──

    @Nested
    class MethodInfoAdditionalTest {
        @Test
        void equals_null() {
            var a = new MethodInfo("m", "()V", 0, 1, 1, new byte[]{1}, List.of());
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        void equals_differentType() {
            var a = new MethodInfo("m", "()V", 0, 1, 1, new byte[]{1}, List.of());
            assertThat(a).isNotEqualTo("not a MethodInfo");
        }

        @Test
        void equals_differentDescriptor() {
            var a = new MethodInfo("m", "()V", 0, 1, 1, new byte[0], List.of());
            var b = new MethodInfo("m", "(I)V", 0, 1, 1, new byte[0], List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentAccessFlags() {
            var a = new MethodInfo("m", "()V", 0, 1, 1, new byte[0], List.of());
            var b = new MethodInfo("m", "()V", 0x0008, 1, 1, new byte[0], List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentMaxStack() {
            var a = new MethodInfo("m", "()V", 0, 1, 1, new byte[0], List.of());
            var b = new MethodInfo("m", "()V", 0, 5, 1, new byte[0], List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_differentMaxLocals() {
            var a = new MethodInfo("m", "()V", 0, 1, 1, new byte[0], List.of());
            var b = new MethodInfo("m", "()V", 0, 1, 5, new byte[0], List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void hashCode_consistency() {
            var a = new MethodInfo("m", "()V", 0, 1, 1, new byte[]{1, 2}, List.of());
            var b = new MethodInfo("m", "()V", 0, 1, 1, new byte[]{1, 2}, List.of());
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_containsHexAndFields() {
            var m = new MethodInfo("process", "(LAPDU;)V", 0x0001, 4, 2,
                    new byte[]{(byte) 0xAB, (byte) 0xCD}, List.of());
            String s = m.toString();
            assertThat(s).contains("process");
            assertThat(s).contains("(LAPDU;)V");
            assertThat(s).contains("abcd");
            assertThat(s).contains("maxStack=4");
            assertThat(s).contains("maxLocals=2");
        }
    }

    // ── JavaCardVersion ──

    @Nested
    class JavaCardVersionTest {
        @Test
        void v3_0_5_shouldHaveFormat2_1() {
            assertThat(JavaCardVersion.V3_0_5.formatMajor()).isEqualTo(2);
            assertThat(JavaCardVersion.V3_0_5.formatMinor()).isEqualTo(1);
        }

        @Test
        void v3_1_0_shouldHaveFormat2_3() {
            assertThat(JavaCardVersion.V3_1_0.formatMajor()).isEqualTo(2);
            assertThat(JavaCardVersion.V3_1_0.formatMinor()).isEqualTo(3);
        }

        @Test
        void v2_1_2_shouldHaveFormat2_1() {
            assertThat(JavaCardVersion.V2_1_2.formatMajor()).isEqualTo(2);
            assertThat(JavaCardVersion.V2_1_2.formatMinor()).isEqualTo(1);
        }

        @Test
        void allVersions_shouldHaveMajor2() {
            for (JavaCardVersion v : JavaCardVersion.values()) {
                assertThat(v.formatMajor()).isEqualTo(2);
            }
        }

        @Test
        void valueOf_shouldWork() {
            assertThat(JavaCardVersion.valueOf("V3_0_5")).isEqualTo(JavaCardVersion.V3_0_5);
        }

        @Test
        void values_shouldReturn8Versions() {
            assertThat(JavaCardVersion.values()).hasSize(8);
        }
    }
}
