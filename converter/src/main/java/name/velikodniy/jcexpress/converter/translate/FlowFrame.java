package name.velikodniy.jcexpress.converter.translate;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract JVM frame of {@link IntFlow}: local variables and operand stack (bottom first). Long
 * and double values are not modelled; the Java Card subset has none (JCVM 3.1 §2.2.1.3).
 *
 * @param locals local variable values by JVM slot
 * @param stack  operand stack values, bottom first
 */
record FlowFrame(FlowValue[] locals, List<FlowValue> stack) {

    FlowFrame copy() {
        return new FlowFrame(locals.clone(), new ArrayList<>(stack));
    }

    void push(FlowValue v) {
        stack.add(v);
    }

    FlowValue pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("operand stack underflow");
        }
        return stack.removeLast();
    }

    /** Pops {@code n} values and returns them bottom first. */
    List<FlowValue> pop(int n) {
        List<FlowValue> popped = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            popped.addFirst(pop());
        }
        return popped;
    }

    FlowValue local(int slot) {
        return locals[slot];
    }

    void setLocal(int slot, FlowValue v) {
        locals[slot] = v;
    }

    /** Adds the values of an incoming frame as inputs of this frame's phi values. */
    void addPhiInputs(FlowFrame incoming) {
        if (incoming.stack.size() != stack.size() || incoming.locals.length != locals.length) {
            throw new IllegalStateException("inconsistent stack height at a join point");
        }
        for (int i = 0; i < locals.length; i++) {
            addInput(locals[i], incoming.locals[i]);
        }
        for (int i = 0; i < stack.size(); i++) {
            addInput(stack.get(i), incoming.stack.get(i));
        }
    }

    private static void addInput(FlowValue phi, FlowValue input) {
        if (phi.op == FlowValue.Op.PHI && !phi.inputs.contains(input)) {
            phi.inputs.add(input);
        }
    }
}
