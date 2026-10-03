package name.velikodniy.jcexpress.api.conformance;

import java.lang.classfile.ClassFile;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The few places where the class files of the reference implementation (jCardSim 3.0.6.0) deviate from the
 * published Java Card 3.0.5 Classic API specification. The corrections are applied to the reference
 * surface before it is compared with the stubs, so the stubs follow the specification, not jCardSim.
 *
 * <p>Each correction states the specification fact it restores. The list is deliberately short; anything
 * else that differs from jCardSim is treated as a stub defect.
 */
final class ReferenceCorrections {

    private static final String JCSYSTEM = "javacard/framework/JCSystem";
    private static final String UTIL = "javacard/framework/Util";
    private static final String USER_EXCEPTION = "javacard/framework/UserException";

    private ReferenceCorrections() {
    }

    /**
     * Returns a copy of the reference surface with the known deviations corrected.
     *
     * @param reference surface read from the jCardSim jar
     * @return corrected surface
     */
    static ApiSurface apply(ApiSurface reference) {
        Map<String, ApiSurface.Type> types = new TreeMap<>(reference.types());
        // JCSystem and Util are static utility classes; the specification documents no constructor for
        // either, while jCardSim declares public no-argument constructors.
        types.computeIfPresent(JCSYSTEM, (name, type) -> withoutMethod(type, "<init>()V"));
        types.computeIfPresent(UTIL, (name, type) -> withoutMethod(type, "<init>()V"));
        // The specification gives UserException a public no-argument constructor (reason 0), and its
        // static throwIt(short) declares the checked UserException. jCardSim omits both.
        types.computeIfPresent(USER_EXCEPTION, (name, type) -> {
            ApiSurface.Type fixed = withMethod(type, new ApiSurface.Method("<init>", "()V",
                    ClassFile.ACC_PUBLIC, Set.of()));
            return withMethod(fixed, new ApiSurface.Method("throwIt", "(S)V",
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, new TreeSet<>(Set.of(USER_EXCEPTION))));
        });
        return new ApiSurface(types);
    }

    private static ApiSurface.Type withoutMethod(ApiSurface.Type type, String key) {
        Map<String, ApiSurface.Method> methods = new TreeMap<>(type.methods());
        methods.remove(key);
        return copy(type, methods);
    }

    private static ApiSurface.Type withMethod(ApiSurface.Type type, ApiSurface.Method method) {
        Map<String, ApiSurface.Method> methods = new TreeMap<>(type.methods());
        methods.put(method.name() + method.descriptor(), method);
        return copy(type, methods);
    }

    private static ApiSurface.Type copy(ApiSurface.Type type, Map<String, ApiSurface.Method> methods) {
        return new ApiSurface.Type(type.name(), type.flags(), type.superName(), type.interfaces(),
                type.fields(), methods);
    }
}
