package io.weidongxu.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class JavaMethodCallParser {

    private static final Logger logger = LoggerFactory.getLogger(JavaMethodCallParser.class);

    private final Function<? super Path, Boolean> fileIsGenerated;

    private final JavaParserFacade parserFacade;
    private final JavaParser parser;

    private final List<MethodInfo> methodCalls = new ArrayList<>();
    private final List<Throwable> aggregatedExceptions = new ArrayList<>();

    public JavaMethodCallParser(Configure configure, Function<? super Path, Boolean> fileIsGenerated) {
        this.fileIsGenerated = fileIsGenerated;

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver(false));
        for (String referenceProject : configure.getReferenceProjects()) {
            combinedTypeSolver.add(new JavaParserTypeSolver(Path.of(referenceProject)));
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        parserFacade = JavaParserFacade.get(combinedTypeSolver);

        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setSymbolResolver(symbolSolver);

        parser = new JavaParser(parserConfiguration);
    }

    public List<MethodInfo> getMethodCalls() {
        return methodCalls;
    }

    public List<Throwable> getAggregatedExceptions() {
        return aggregatedExceptions;
    }

    public void processJavaFile(Path javaFile) {
        try {
            if (fileIsGenerated.apply(javaFile)) {
                return;
            }

            ParseResult<CompilationUnit> parseResultCompilationUnit = parser.parse(javaFile);
            CompilationUnit compilationUnit = parseResultCompilationUnit.getResult().get();

            logger.info("Processing file: {}", javaFile.toString());

            VoidVisitor<Abort> visitor = new VoidVisitorAdapter<>() {
                @Override
                public void visit(MethodCallExpr n, Abort arg) {
                    if (arg.abort) {
                        return;
                    }

                    super.visit(n, arg);

                    String methodName = n.getName().getIdentifier();
                    if ((methodName.startsWith("is") && methodName.length() > 2)
                            || (methodName.startsWith("get") && methodName.length() > 3)
                            || (methodName.startsWith("set") && methodName.length() > 3)) {
                        int lineNumber = n.getBegin().get().line;
                        logger.info("Candidate method call: line {}, {}", lineNumber, methodName);
                        try {
                            SymbolReference<ResolvedMethodDeclaration> methodReference = parserFacade.solve(n);
                            if (methodReference.isSolved()) {
                                ResolvedMethodDeclaration resolvedMethodDeclaration = methodReference.getCorrespondingDeclaration();
                                String fullClassName = resolvedMethodDeclaration.declaringType().getId();
                                MethodInfo methodCall = new MethodInfo(javaFile, lineNumber, methodName,
                                        fullClassName);
                                methodCalls.add(methodCall);
                            }
                        } catch (UnsolvedSymbolException e) {
                            aggregatedExceptions.add(e);
                        } catch (StackOverflowError e) {
                            aggregatedExceptions.add(e);
                            arg.abort = true;
                        }
                    }
                }
            };
            visitor.visit(compilationUnit, new Abort());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Abort {
        private boolean abort = false;
    }
}
