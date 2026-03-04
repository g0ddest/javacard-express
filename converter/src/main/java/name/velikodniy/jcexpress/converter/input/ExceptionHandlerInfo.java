package name.velikodniy.jcexpress.converter.input;

/**
 * A single exception handler entry extracted from a method's JVM {@code Code}
 * attribute exception table.
 *
 * <h2>Converter Pipeline: Stage 1 (Load)</h2>
 * <p>Each {@code ExceptionHandlerInfo} is produced by {@link ClassFileReader}
 * when parsing a method's {@code Code} attribute. It records the bytecode
 * ranges of the protected (try) block and the handler entry point, along with
 * the caught exception type.
 *
 * <h2>Usage in Later Stages</h2>
 * <ul>
 *   <li><strong>Stage 5 (Bytecode Translation):</strong> The JVM bytecode
 *       offsets ({@code startPc}, {@code endPc}, {@code handlerPc}) are
 *       remapped to JCVM bytecode offsets during translation.</li>
 *   <li><strong>Stage 6 (CAP Generation):</strong> The translated exception
 *       handlers are emitted into the Method Component's exception handler
 *       table (JCVM 3.0.5 spec Section 6.10, {@code exception_handler_info}
 *       structure).</li>
 * </ul>
 *
 * <h2>JCVM Spec References</h2>
 * <ul>
 *   <li>Section 6.10 — Method Component: defines the
 *       {@code exception_handler_info} structure with fields
 *       {@code start_offset}, {@code active_length}, {@code handler_offset},
 *       and {@code catch_type_index}.</li>
 * </ul>
 *
 * @param startPc   JVM bytecode offset of the start of the try block (inclusive)
 * @param endPc     JVM bytecode offset of the end of the try block (exclusive)
 * @param handlerPc JVM bytecode offset of the start of the handler (catch/finally) code
 * @param catchType internal name of the caught exception class
 *                  (e.g. {@code "javacard/framework/ISOException"}),
 *                  or {@code null} for a finally block (catches all exceptions)
 * @see MethodInfo
 * @see ClassFileReader
 */
public record ExceptionHandlerInfo(
        int startPc,
        int endPc,
        int handlerPc,
        String catchType
) {}
