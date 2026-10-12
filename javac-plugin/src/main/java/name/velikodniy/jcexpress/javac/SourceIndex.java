package name.velikodniy.jcexpress.javac;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import name.velikodniy.jcexpress.converter.PackageCheck;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The source trees of the classes of the compilation, recorded when javac has analysed them, to report a problem
 * that the converter found in a class file at the place in the source it comes from: named, nested, local and
 * anonymous classes by binary name, their methods by name and descriptor, their fields by name, and code by line.
 * Everything is recorded before javac lowers the trees for the class files (it moves initializers into methods, for
 * example), so that later lookups see the source as written. A line on which no tree starts (an expression continued
 * from the line before) is the innermost tree around the start of the line; a class javac adds without a source of
 * its own (such as {@code Outer$1} for a switch on an enum) is reported in the class whose name it extends.
 */
final class SourceIndex {

    /**
     * A place in the source.
     *
     * @param tree the tree to report at
     * @param unit the source file
     */
    record Place(Tree tree, CompilationUnitTree unit) {
    }

    /**
     * One class: its declarations and its trees by line, in the whole class and in each method.
     */
    private record SourceClass(ClassTree tree, CompilationUnitTree unit, Map<String, Tree> members,
                               Map<String, List<Tree>> methodsByName, Map<String, Tree> interfaces,
                               LineIndex lines, Map<Tree, LineIndex> memberLines) {
    }

    /** Where a tree is in its source file. */
    private record Span(long start, long end, Tree tree) {
    }

    /**
     * The trees below one root (a class or a method) by line: the first one that starts on each line, and for a line
     * on which none starts the innermost one around the start of the line. A compilation without line numbers
     * ({@code -g:none}) has no line map: its index is empty, and its class files name no lines either.
     */
    private record LineIndex(Map<Long, Tree> first, List<Span> spans, LineMap lineMap) {

        Tree at(long line) {
            Tree tree = first.get(line);
            if (tree != null || line <= 0 || lineMap == null) {
                return tree;
            }
            long lineStart;
            try {
                lineStart = lineMap.getStartPosition(line);
            } catch (RuntimeException e) {
                return null;
            }
            Span inner = null;
            for (Span span : spans) {
                if (span.start() < lineStart && lineStart < span.end()
                        && (inner == null || span.end() - span.start() < inner.end() - inner.start())) {
                    inner = span;
                }
            }
            return inner == null ? null : inner.tree();
        }
    }

    private final Trees trees;
    private final Elements elements;
    private final Types types;
    private final Map<String, SourceClass> classes = new HashMap<>();

    SourceIndex(Trees trees, Elements elements, Types types) {
        this.trees = trees;
        this.elements = elements;
        this.types = types;
    }

    /**
     * Records an analysed top-level class with every class declared in it.
     *
     * @param analyzed the class
     * @param unit     its source file
     */
    void add(TypeElement analyzed, CompilationUnitTree unit) {
        TreePath path = analyzed == null ? null : trees.getPath(analyzed);
        if (path == null) {
            return;
        }
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void unused) {
                Element element = trees.getElement(getCurrentPath());
                if (element instanceof TypeElement type) {
                    classes.put(elements.getBinaryName(type).toString(), sourceClass(getCurrentPath(), unit));
                }
                return super.visitClass(node, unused);
            }
        }.scan(path, null);
    }

    /**
     * Finds where a problem comes from: code by its line inside its member (or elsewhere in the class, where field
     * initializers and initializer blocks are), a declaration at the member, otherwise the class. A class javac added
     * without a source of its own is looked up by line in the class whose binary name it extends.
     *
     * @param problem the problem
     * @param unit    the source file javac compiled the problem's class from
     * @return the place; the source file itself when no class of it can be found
     */
    Place locate(PackageCheck.Problem problem, CompilationUnitTree unit) {
        String name = problem.className().replace('/', '.');
        SourceClass source = classes.get(name);
        if (source != null) {
            return place(source, problem);
        }
        for (int dollar = name.lastIndexOf('$'); dollar > 0; dollar = name.lastIndexOf('$', dollar - 1)) {
            SourceClass enclosing = classes.get(name.substring(0, dollar));
            if (enclosing != null) {
                Tree tree = enclosing.lines().at(problem.line());
                return new Place(tree == null ? enclosing.tree() : tree, enclosing.unit());
            }
        }
        return new Place(unit, unit);
    }

    private static Place place(SourceClass source, PackageCheck.Problem problem) {
        Tree member = member(source, problem.member());
        long line = problem.line();
        Tree tree = null;
        if (!problem.declaration() && line > 0) {
            LineIndex memberLines = member == null ? null : source.memberLines().get(member);
            tree = memberLines == null ? null : memberLines.at(line);
            tree = tree == null ? source.lines().at(line) : tree;
        }
        if (tree == null) {
            tree = member != null || line <= 0 ? member : source.lines().at(line);
        }
        return new Place(tree == null ? source.tree() : tree, source.unit());
    }

    private SourceClass sourceClass(TreePath classPath, CompilationUnitTree unit) {
        ClassTree tree = (ClassTree) classPath.getLeaf();
        Map<String, Tree> members = new HashMap<>();
        Map<String, List<Tree>> methodsByName = new HashMap<>();
        Map<Tree, LineIndex> memberLines = new IdentityHashMap<>();
        for (Tree member : tree.getMembers()) {
            if (member instanceof VariableTree field) {
                members.put("field " + field.getName(), field);
            } else if (member instanceof MethodTree method
                    && trees.getElement(new TreePath(classPath, method)) instanceof ExecutableElement executable) {
                String name = executable.getSimpleName().toString();
                members.put(name + Descriptors.of(executable, types, elements), method);
                methodsByName.computeIfAbsent(name, n -> new ArrayList<>()).add(method);
                memberLines.put(method, lines(method, unit));
            }
        }
        return new SourceClass(tree, unit, members, methodsByName, interfaces(classPath, tree), lines(tree, unit),
                memberLines);
    }

    /** The trees of the {@code implements} clause (of an interface: its {@code extends} clause) by binary name. */
    private Map<String, Tree> interfaces(TreePath classPath, ClassTree tree) {
        Map<String, Tree> interfaces = new HashMap<>();
        for (Tree type : tree.getImplementsClause()) {
            if (trees.getElement(new TreePath(classPath, type)) instanceof TypeElement typeElement) {
                interfaces.put(elements.getBinaryName(typeElement).toString(), type);
            }
        }
        return interfaces;
    }

    /**
     * The declaration a problem's member names: the class for {@code class}, the {@code extends} clause for
     * {@code superclass}, the named interface, a field, or a method by name and descriptor (by name alone when the
     * descriptor differs, as for constructors to which javac gives more parameters).
     */
    private static Tree member(SourceClass source, String member) {
        if (member.equals("class")) {
            return source.tree();
        }
        if (member.equals("superclass")) {
            return source.tree().getExtendsClause();
        }
        if (member.startsWith("interface ")) {
            return source.interfaces().get(member.substring("interface ".length()).replace('/', '.'));
        }
        Tree declared = source.members().get(member);
        int parenthesis = member.indexOf('(');
        if (declared != null || parenthesis < 0) {
            return declared;
        }
        List<Tree> sameName = source.methodsByName().getOrDefault(member.substring(0, parenthesis), List.of());
        return sameName.size() == 1 ? sameName.getFirst() : null;
    }

    /** The trees below {@code root}: for every line the first one in source order that starts on it, and all spans. */
    private LineIndex lines(Tree root, CompilationUnitTree unit) {
        SourcePositions positions = trees.getSourcePositions();
        LineMap lineMap = unit.getLineMap();
        Map<Long, Tree> first = new HashMap<>();
        List<Span> spans = new ArrayList<>();
        if (lineMap == null) {
            return new LineIndex(first, spans, null);
        }
        new TreeScanner<Void, Void>() {
            @Override
            public Void scan(Tree tree, Void unused) {
                if (tree == null) {
                    return null;
                }
                long start = positions.getStartPosition(unit, tree);
                long end = positions.getEndPosition(unit, tree);
                if (tree != root && start >= 0) {
                    first.putIfAbsent(lineMap.getLineNumber(start), tree);
                    if (end > start) {
                        spans.add(new Span(start, end, tree));
                    }
                }
                return super.scan(tree, unused);
            }
        }.scan(root, null);
        return new LineIndex(first, spans, lineMap);
    }
}
