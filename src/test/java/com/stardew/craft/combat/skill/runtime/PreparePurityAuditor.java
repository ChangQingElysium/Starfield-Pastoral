package com.stardew.craft.combat.skill.runtime;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** JDK-AST based fail-closed audit of runtime skill preparation. */
final class PreparePurityAuditor {
    private static final Set<Tree.Kind> MUTATING_UNARY = Set.of(
            Tree.Kind.PREFIX_INCREMENT,
            Tree.Kind.PREFIX_DECREMENT,
            Tree.Kind.POSTFIX_INCREMENT,
            Tree.Kind.POSTFIX_DECREMENT
    );

    private PreparePurityAuditor() {}

    static AuditResult auditPaths(
            List<Path> sources,
            Set<String> allowedSignatures
    ) throws IOException {
        JavaCompiler compiler = Objects.requireNonNull(
                ToolProvider.getSystemJavaCompiler(),
                "Prepare purity audit requires a JDK compiler"
        );
        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics,
                null,
                null
        )) {
            return audit(
                    compiler,
                    files.getJavaFileObjectsFromPaths(sources),
                    allowedSignatures,
                    diagnostics
            );
        }
    }

    static AuditResult auditSource(
            String className,
            String source,
            Set<String> allowedSignatures
    ) throws IOException {
        JavaCompiler compiler = Objects.requireNonNull(
                ToolProvider.getSystemJavaCompiler(),
                "Prepare purity audit requires a JDK compiler"
        );
        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();
        JavaFileObject file = new StringSource(className, source);
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics,
                null,
                null
        )) {
            return audit(
                    compiler,
                    List.of(file),
                    allowedSignatures,
                    diagnostics
            );
        }
    }

    private static AuditResult audit(
            JavaCompiler compiler,
            Iterable<? extends JavaFileObject> sources,
            Set<String> allowedSignatures,
            DiagnosticCollector<JavaFileObject> diagnostics
    ) throws IOException {
        List<String> options = List.of(
                "-proc:none",
                "-classpath",
                System.getProperty("java.class.path")
        );
        JavacTask task = (JavacTask) compiler.getTask(
                null,
                null,
                diagnostics,
                options,
                null,
                sources
        );
        List<CompilationUnitTree> units = new ArrayList<>();
        task.parse().forEach(units::add);
        task.analyze();

        List<String> compilerErrors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind()
                        == Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .toList();
        if (!compilerErrors.isEmpty()) {
            return new AuditResult(Set.of(), compilerErrors);
        }

        Trees trees = Trees.instance(task);
        Types types = task.getTypes();
        Map<ExecutableElement, TreePath> declarations = new HashMap<>();
        for (CompilationUnitTree unit : units) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethod(MethodTree node, Void unused) {
                    Element element = trees.getElement(getCurrentPath());
                    if (element instanceof ExecutableElement executable) {
                        declarations.put(executable, getCurrentPath());
                    }
                    return super.visitMethod(node, unused);
                }
            }.scan(unit, null);
        }

        Set<String> actual = new TreeSet<>();
        List<String> violations = new ArrayList<>();
        Set<ExecutableElement> scannedHelpers = new HashSet<>();
        for (CompilationUnitTree unit : units) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethod(MethodTree node, Void unused) {
                    if (!node.getName().contentEquals("begin")
                            || node.getParameters().size() != 2
                            || node.getBody() == null) {
                        return null;
                    }
                    Element element = trees.getElement(getCurrentPath());
                    if (!(element instanceof ExecutableElement begin)) {
                        violations.add(location(unit, node, trees)
                                + " unresolved begin method");
                        return null;
                    }
                    new PrepareScanner(
                            unit,
                            begin,
                            trees,
                            types,
                            declarations,
                            scannedHelpers,
                            allowedSignatures,
                            actual,
                            violations
                    ).scan(new TreePath(getCurrentPath(), node.getBody()), null);
                    return null;
                }
            }.scan(unit, null);
        }
        return new AuditResult(Set.copyOf(actual), List.copyOf(violations));
    }

    record AuditResult(Set<String> actualSignatures, List<String> violations) {
        boolean valid() {
            return violations.isEmpty();
        }
    }

    private static final class PrepareScanner
            extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final TypeElement handlerType;
        private final Trees trees;
        private final Types types;
        private final Map<ExecutableElement, TreePath> declarations;
        private final Set<ExecutableElement> scannedHelpers;
        private final Set<String> allowed;
        private final Set<String> actual;
        private final List<String> violations;

        private PrepareScanner(
                CompilationUnitTree unit,
                ExecutableElement begin,
                Trees trees,
                Types types,
                Map<ExecutableElement, TreePath> declarations,
                Set<ExecutableElement> scannedHelpers,
                Set<String> allowed,
                Set<String> actual,
                List<String> violations
        ) {
            this.unit = unit;
            this.handlerType = (TypeElement) begin.getEnclosingElement();
            this.trees = trees;
            this.types = types;
            this.declarations = declarations;
            this.scannedHelpers = scannedHelpers;
            this.allowed = allowed;
            this.actual = actual;
            this.violations = violations;
        }

        @Override
        public Void visitMethodInvocation(
                MethodInvocationTree node,
                Void unused
        ) {
            Element element = trees.getElement(getCurrentPath());
            if (!(element instanceof ExecutableElement executable)) {
                violations.add(location(unit, node, trees)
                        + " unresolved invocation " + node.getMethodSelect());
                return null;
            }
            String signature = signature(executable, types);
            recordAllowed(node, signature);

            if (isCallbackRegistration(executable, signature, allowed)) {
                for (ExpressionTree argument : node.getArguments()) {
                    if (!(argument instanceof LambdaExpressionTree)
                            && !(argument instanceof MemberReferenceTree)) {
                        scan(argument, unused);
                    }
                }
                return null;
            }

            scanHelper(executable, unused);
            return super.visitMethodInvocation(node, unused);
        }

        @Override
        public Void visitMemberReference(
                MemberReferenceTree node,
                Void unused
        ) {
            Element element = trees.getElement(getCurrentPath());
            if (!(element instanceof ExecutableElement executable)) {
                violations.add(location(unit, node, trees)
                        + " unresolved member reference " + node);
                return null;
            }
            recordAllowed(node, signature(executable, types));
            scanHelper(executable, unused);
            return super.visitMemberReference(node, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (!(element instanceof ExecutableElement constructor)) {
                violations.add(location(unit, node, trees)
                        + " unresolved constructor " + node.getIdentifier());
            } else {
                recordAllowed(node, signature(constructor, types));
            }
            return super.visitNewClass(node, unused);
        }

        @Override
        public Void visitAssignment(AssignmentTree node, Void unused) {
            checkWrite(node.getVariable(), node);
            return super.visitAssignment(node, unused);
        }

        @Override
        public Void visitCompoundAssignment(
                CompoundAssignmentTree node,
                Void unused
        ) {
            checkWrite(node.getVariable(), node);
            return super.visitCompoundAssignment(node, unused);
        }

        @Override
        public Void visitUnary(UnaryTree node, Void unused) {
            if (MUTATING_UNARY.contains(node.getKind())) {
                checkWrite(node.getExpression(), node);
            }
            return super.visitUnary(node, unused);
        }

        private void recordAllowed(Tree node, String signature) {
            actual.add(signature);
            if (!allowed.contains(signature)) {
                violations.add(location(unit, node, trees)
                        + " unknown prepare operation " + signature);
            }
        }

        private void scanHelper(
                ExecutableElement executable,
                Void unused
        ) {
            Element owner = executable.getEnclosingElement();
            if (!owner.equals(handlerType)
                    || !declarations.containsKey(executable)
                    || !scannedHelpers.add(executable)) {
                return;
            }
            MethodTree declaration = (MethodTree)
                    declarations.get(executable).getLeaf();
            if (declaration.getBody() == null) {
                return;
            }
            new PrepareScanner(
                    unit,
                    executable,
                    trees,
                    types,
                    declarations,
                    scannedHelpers,
                    allowed,
                    actual,
                    violations
            ).scan(new TreePath(
                            declarations.get(executable),
                            declaration.getBody()
                    ), unused);
        }

        private void checkWrite(ExpressionTree target, Tree operation) {
            if (target instanceof IdentifierTree identifier) {
                Element element = trees.getElement(
                        new TreePath(getCurrentPath(), identifier)
                );
                if (element != null && (element.getKind()
                        == ElementKind.LOCAL_VARIABLE
                        || element.getKind() == ElementKind.PARAMETER)) {
                    return;
                }
            }
            violations.add(location(unit, operation, trees)
                    + " non-local write " + target);
        }
    }

    private static boolean isCallbackRegistration(
            ExecutableElement executable,
            String signature,
            Set<String> allowed
    ) {
        String methodName = executable.getSimpleName().toString();
        if (!methodName.equals("registerCommittedEffect")
                && !methodName.equals("registerBeginFailureCleanup")) {
            return false;
        }
        if (!(executable.getEnclosingElement() instanceof TypeElement owner)) {
            return false;
        }
        return owner.getQualifiedName().contentEquals(
                SkillInstance.class.getName()
        ) || allowed.contains(signature);
    }

    static String signature(ExecutableElement executable, Types types) {
        TypeElement owner = (TypeElement) executable.getEnclosingElement();
        String parameters = executable.getParameters().stream()
                .map(parameter -> erased(parameter.asType(), types))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return owner.getQualifiedName() + "#"
                + executable.getSimpleName() + "(" + parameters + ")";
    }

    private static String erased(TypeMirror type, Types types) {
        return types.erasure(type).toString();
    }

    private static String location(
            CompilationUnitTree unit,
            Tree tree,
            Trees trees
    ) {
        long position = trees.getSourcePositions().getStartPosition(unit, tree);
        long line = position < 0L
                ? -1L
                : unit.getLineMap().getLineNumber(position);
        return unit.getSourceFile().getName() + ":" + line;
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String source;

        private StringSource(String className, String source) {
            super(
                    URI.create("string:///"
                            + className.replace('.', '/')
                            + Kind.SOURCE.extension),
                    Kind.SOURCE
            );
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
