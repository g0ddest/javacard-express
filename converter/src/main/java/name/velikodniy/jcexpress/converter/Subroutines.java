package name.velikodniy.jcexpress.converter;

import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction.JsrInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction.RetInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The subroutines of one method and the checks that make inlining them exact.
 *
 * <p>{@code jsr} pushes the address of the next instruction and jumps to the subroutine; the subroutine
 * stores the address in a local variable, and {@code ret} jumps to the address held by a local variable
 * (JCVM 3.1 §7.5.69, §7.5.79; JVMS 6.5). The code is divided into {@linkplain Level levels}, as {@code finally}
 * blocks nest: the method's level is what it reaches from its entry without entering a subroutine, along jumps,
 * fall-through, exception handlers of the instructions (JVMS 2.10) and from a {@code jsr} to the next
 * instruction; a subroutine's level is what it reaches from its entry the same way without the code of the
 * levels around it. A subroutine belongs inside the outermost level that calls it (the level of the
 * {@code try} statement whose {@code finally} block it is), and that level is around every other level that
 * calls it.
 *
 * <p>The {@linkplain Body body} of a subroutine is what runs from its entry until a {@code ret}: its level's
 * code reachable from the entry, where the instruction after a nested {@code jsr} counts only if that
 * subroutine can return. A body that reaches the code of a level around it leaves the subroutine for good
 * there ({@code continue}, {@code break}, falling through, or a handler of an enclosing {@code try} in a
 * {@code finally} block): those instructions are its exits. A level's body also takes in the exits into its
 * own code of the subroutines it runs.
 *
 * <p>A copy of a body per call is exact if every {@code ret} of a subroutine returns from that subroutine:
 * the method's body has no {@code ret}; a subroutine that returns stores its return address with
 * {@code astore} at its entry, every {@code ret} in its body reads that local, nothing it runs writes the local
 * before a {@code ret} is reached, it is entered only by {@code jsr}, and it does not call itself (JVMS 4.9.2).
 * javac and ECJ write subroutines that way; other shapes are reported as {@link Unsupported}.
 */
final class Subroutines {

    /** Most instructions the inlined method may have; a method holds at most 65535 bytes of code (JVMS 4.7.3). */
    static final int MAX_INSTRUCTIONS = 65535;
    /** Saturation value of the size computation (exponential in the nesting depth). */
    private static final long SIZE_CAP = MAX_INSTRUCTIONS * 2L + 1;

    /** The code that runs from an entry until a {@code ret} (instruction numbers of {@link IndexedCode}). */
    static final class Body {
        final int entry;
        final BitSet members = new BitSet();
        final BitSet rets = new BitSet();
        /** Code of a level around this one, where the body continues when it leaves without {@code ret}. */
        final BitSet exits = new BitSet();
        /** {@code jsr} instruction number to the entry of the subroutine it calls. */
        final Map<Integer, Integer> calls = new TreeMap<>();

        Body(int entry) {
            this.entry = entry;
        }

        boolean returns() {
            return !rets.isEmpty();
        }
    }

    /**
     * A level of the code: the method's own (no parent) or a subroutine's.
     *
     * @param entry  the first instruction
     * @param parent the level around this one: the outermost level that calls the subroutine
     * @param own    the code of this level (a {@code jsr} counts as continuing with the next instruction)
     * @param stop   the code of all levels around this one, where this level's code ends
     */
    private record Level(int entry, Level parent, BitSet own, BitSet stop) {
        boolean inside(Level other) {
            for (Level level = this; level != null; level = level.parent()) {
                if (level == other) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A subroutine shape that is not inlined: the instruction number it was found at and why. */
    static final class Unsupported extends RuntimeException {
        private final int index;

        Unsupported(int index, String reason) {
            super(reason, null, false, false);
            this.index = index;
        }

        int index() {
            return index;
        }
    }

    private final IndexedCode code;
    private final Level mainLevel;
    private final Map<Integer, Level> levels = new HashMap<>();
    private final Map<Integer, Body> subroutines = new HashMap<>();
    private final Set<Integer> computing = new HashSet<>();
    private final Body main;

    /**
     * Finds and checks the subroutines of a method.
     *
     * @throws Unsupported if a subroutine cannot be inlined exactly or the inlined code would be too large
     */
    Subroutines(IndexedCode code) {
        this.code = code;
        this.mainLevel = levels();
        this.main = levelBody(mainLevel);
        if (main.returns()) {
            throw unsupported(main.rets.nextSetBit(0), "ret at bci %d is not inside a subroutine: it is reached"
                    + " without a jsr", code.bci(main.rets.nextSetBit(0)));
        }
        checkCallers(main, mainLevel);
        for (Body sub : calledFrom(main)) {
            checkReturnAddress(sub);
            checkCallers(sub, levels.get(sub.entry));
        }
        long size = copiedInstructions(main, new IdentityHashMap<>());
        if (size > MAX_INSTRUCTIONS) {
            throw unsupported(0, "inlining the subroutines gives %s instructions, more than a method can hold"
                    + " (at most %d bytes of code, JVMS 4.7.3)", size > SIZE_CAP - 1 ? "over " + (SIZE_CAP - 1)
                    : Long.toString(size), MAX_INSTRUCTIONS);
        }
    }

    /** The method's own body (entry 0, no exits). */
    Body main() {
        return main;
    }

    /** The subroutine called by the {@code jsr} with the given number. */
    Body callee(int jsr) {
        return subroutine(code.index(((JsrInstruction) code.at(jsr)).target()));
    }

    // ---- levels

    /**
     * Assigns every subroutine called from a level's code to a level, outermost callers first, so that a
     * subroutine belongs inside the outermost level that calls it.
     */
    private Level levels() {
        Level methodLevel = new Level(0, null, levelCode(0, new BitSet()), new BitSet());
        Deque<Level> work = new ArrayDeque<>(List.of(methodLevel));
        while (!work.isEmpty()) {
            Level caller = work.poll();
            for (int i = caller.own().nextSetBit(0); i >= 0; i = caller.own().nextSetBit(i + 1)) {
                if (code.at(i) instanceof JsrInstruction jsr && !levels.containsKey(code.index(jsr.target()))) {
                    BitSet stop = (BitSet) caller.stop().clone();
                    stop.or(caller.own());
                    int entry = code.index(jsr.target());
                    Level level = new Level(entry, caller, levelCode(entry, stop), stop);
                    levels.put(entry, level);
                    work.add(level);
                }
            }
        }
        return methodLevel;
    }

    /** The code reachable from an entry without the stop code, a {@code jsr} continuing with the next instruction. */
    private BitSet levelCode(int entry, BitSet stop) {
        BitSet reached = new BitSet();
        Deque<Integer> work = new ArrayDeque<>(List.of(entry));
        while (!work.isEmpty()) {
            int i = work.pop();
            if (reached.get(i) || stop.get(i)) {
                continue;
            }
            reached.set(i);
            if (code.at(i) instanceof JsrInstruction) {
                if (i + 1 < code.size()) {
                    work.add(i + 1);
                }
                work.addAll(code.handlersOf(i));
            } else {
                work.addAll(edges(i));
            }
        }
        return reached;
    }

    /**
     * Every subroutine with exits that a body calls must belong to the body's level or a level around it, so that
     * its exits lie in the code of a copy that encloses each of its copies.
     */
    private void checkCallers(Body body, Level level) {
        for (Map.Entry<Integer, Integer> call : body.calls.entrySet()) {
            Level callee = levels.get(call.getValue());
            if (!subroutine(call.getValue()).exits.isEmpty() && !level.inside(callee.parent())) {
                throw unsupported(call.getKey(), "the subroutine at bci %d is called at bci %d from a finally block"
                        + " that is not inside the code around it", code.bci(call.getValue()), code.bci(call.getKey()));
            }
        }
    }

    // ---- bodies

    /**
     * The body of a level: its code reachable from its entry, and from the exits into its code of the
     * subroutines it runs (directly or through other subroutines).
     */
    private Body levelBody(Level level) {
        Body body = new Body(level.entry());
        Deque<Integer> work = new ArrayDeque<>(List.of(level.entry()));
        while (!work.isEmpty()) {
            grow(body, work, level.stop());
            for (Body sub : calledFrom(body)) {
                for (int x = sub.exits.nextSetBit(0); x >= 0; x = sub.exits.nextSetBit(x + 1)) {
                    if (level.own().get(x) && !body.members.get(x)) {
                        work.add(x);
                    }
                }
            }
        }
        return body;
    }

    /** The subroutine that starts at an instruction (a {@code jsr} target). */
    private Body subroutine(int entry) {
        Body body = subroutines.get(entry);
        if (body != null) {
            return body;
        }
        if (!computing.add(entry)) {
            throw new IllegalStateException("subroutine at instruction " + entry + " is being computed");
        }
        body = levelBody(levels.get(entry));
        computing.remove(entry);
        subroutines.put(entry, body);
        return body;
    }

    /** Adds what the work list reaches to the body; instructions of {@code stop} become exits. */
    private void grow(Body body, Deque<Integer> work, BitSet stop) {
        while (!work.isEmpty()) {
            int i = work.pop();
            if (body.members.get(i) || body.exits.get(i)) {
                continue;
            }
            if (stop.get(i)) {
                body.exits.set(i);
                continue;
            }
            body.members.set(i);
            if (code.at(i) instanceof RetInstruction) {
                body.rets.set(i);
            } else if (code.at(i) instanceof JsrInstruction jsr) {
                body.calls.put(i, code.index(jsr.target()));
                checkNotRecursive(i, code.index(jsr.target()));
            }
            work.addAll(edges(i));
        }
    }

    private void checkNotRecursive(int jsr, int target) {
        if (computing.contains(target)) {
            throw unsupported(jsr, "the subroutine at bci %d reaches a jsr to itself at bci %d (a recursive call,"
                    + " JVMS 4.9.2)", code.bci(target), code.bci(jsr));
        }
    }

    /** Where control can go from an instruction: normal successors and exception handlers (JVMS 2.10). */
    private List<Integer> edges(int i) {
        List<Integer> next = new ArrayList<>(successors(i));
        next.addAll(code.handlersOf(i));
        for (int n : next) {
            if (n >= code.size()) {
                throw unsupported(i, "execution falls off the end of the code after bci %d (JVMS 4.9.2)",
                        code.bci(i));
            }
        }
        return next;
    }

    /** Normal successors; after a {@code jsr} the next instruction only if the subroutine can return. */
    private List<Integer> successors(int i) {
        Instruction insn = code.at(i);
        return switch (insn) {
            case JsrInstruction jsr -> subroutine(code.index(jsr.target())).returns() ? List.of(i + 1) : List.of();
            case RetInstruction r -> List.of();
            case ReturnInstruction r -> List.of();
            case ThrowInstruction t -> List.of();
            case BranchInstruction b -> b.opcode() == Opcode.GOTO || b.opcode() == Opcode.GOTO_W
                    ? List.of(code.index(b.target())) : List.of(code.index(b.target()), i + 1);
            case TableSwitchInstruction t -> targets(t.defaultTarget(), t.cases());
            case LookupSwitchInstruction l -> targets(l.defaultTarget(), l.cases());
            default -> List.of(i + 1);
        };
    }

    private List<Integer> targets(Label defaultTarget, List<SwitchCase> cases) {
        List<Integer> targets = new ArrayList<>();
        targets.add(code.index(defaultTarget));
        cases.forEach(c -> targets.add(code.index(c.target())));
        return targets;
    }

    /** The subroutines a body calls, directly or through other subroutines. */
    private List<Body> calledFrom(Body body) {
        List<Body> found = new ArrayList<>();
        Deque<Body> work = new ArrayDeque<>(List.of(body));
        Set<Integer> seen = new HashSet<>();
        while (!work.isEmpty()) {
            for (int entry : work.pop().calls.values()) {
                if (seen.add(entry)) {
                    found.add(subroutine(entry));
                    work.add(subroutine(entry));
                }
            }
        }
        return found;
    }

    // ---- return address checks

    private void checkReturnAddress(Body sub) {
        if (!sub.returns()) {
            return;  // the return address is never used
        }
        int slot = returnAddressSlot(sub);
        for (int r = sub.rets.nextSetBit(0); r >= 0; r = sub.rets.nextSetBit(r + 1)) {
            int used = ((RetInstruction) code.at(r)).slot();
            if (used != slot) {
                throw unsupported(r, "ret at bci %d uses local %d, but the subroutine at bci %d keeps its return"
                        + " address in local %d (a return to an outer subroutine)", code.bci(r), used,
                        code.bci(sub.entry), slot);
            }
        }
        for (int w = sub.members.nextSetBit(0); w >= 0; w = sub.members.nextSetBit(w + 1)) {
            if (w != sub.entry && writes(w, slot, new HashSet<>())) {
                checkNoRetAfter(sub, w, slot);
            }
        }
    }

    private int returnAddressSlot(Body sub) {
        if (!(code.at(sub.entry) instanceof StoreInstruction store) || store.typeKind() != TypeKind.REFERENCE) {
            throw unsupported(sub.entry, "the subroutine at bci %d returns, but does not store its return address"
                    + " with astore first", code.bci(sub.entry));
        }
        for (int i = sub.members.nextSetBit(0); i >= 0; i = sub.members.nextSetBit(i + 1)) {
            if (edges(i).contains(sub.entry)) {
                throw unsupported(i, "the subroutine at bci %d is entered from bci %d by a jump or an exception"
                        + " handler, not by jsr", code.bci(sub.entry), code.bci(i));
            }
        }
        return store.slot();
    }

    /**
     * Whether the instruction can leave the local written: a store or an iinc of it, or the call of a subroutine
     * that writes it and can still return afterwards.
     */
    private boolean writes(int i, int slot, Set<Integer> visitedCallees) {
        return switch (code.at(i)) {
            case StoreInstruction s -> s.slot() == slot || (s.slot() + 1 == slot && s.typeKind().slotSize() == 2);
            case IncrementInstruction inc -> inc.slot() == slot;
            case JsrInstruction jsr -> writesBeforeReturn(subroutine(code.index(jsr.target())), slot, visitedCallees);
            default -> false;
        };
    }

    private boolean writesBeforeReturn(Body callee, int slot, Set<Integer> visitedCallees) {
        if (!callee.returns() || !visitedCallees.add(callee.entry)) {
            return false;
        }
        for (int i = callee.members.nextSetBit(0); i >= 0; i = callee.members.nextSetBit(i + 1)) {
            if (writes(i, slot, visitedCallees) && retAfter(callee, i) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** A write of the return address local is harmless only if no {@code ret} of the subroutine follows it. */
    private void checkNoRetAfter(Body sub, int write, int slot) {
        int ret = retAfter(sub, write);
        if (ret >= 0) {
            throw unsupported(write, "local %d, which holds the return address of the subroutine at bci %d, is"
                    + " written at bci %d before the ret at bci %d", slot, code.bci(sub.entry), code.bci(write),
                    code.bci(ret));
        }
    }

    /** A {@code ret} of the body reachable from the instruction (exception handlers included), or -1. */
    private int retAfter(Body body, int from) {
        BitSet reached = new BitSet();
        Deque<Integer> work = new ArrayDeque<>(edges(from));
        while (!work.isEmpty()) {
            int i = work.pop();
            if (!reached.get(i) && body.members.get(i)) {
                reached.set(i);
                if (body.rets.get(i)) {
                    return i;
                }
                work.addAll(edges(i));
            }
        }
        return -1;
    }

    // ---- size

    /** Instructions of the inlined code: each copy of a body with a jump per exit, one more per call. */
    private long copiedInstructions(Body body, Map<Body, Long> memo) {
        Long known = memo.get(body);
        if (known != null) {
            return known;
        }
        long count = body.members.cardinality() + body.exits.cardinality();
        for (int callee : body.calls.values()) {
            count = Math.min(count + 1 + copiedInstructions(subroutine(callee), memo), SIZE_CAP);
        }
        memo.put(body, count);
        return count;
    }

    private static Unsupported unsupported(int index, String format, Object... args) {
        return new Unsupported(index, String.format(Locale.ROOT, format, args));
    }
}
