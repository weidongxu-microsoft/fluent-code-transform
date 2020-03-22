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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    static CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
    static JavaSymbolSolver symbolSolver;
    static JavaParserFacade parserFacade;
    static ParserConfiguration parserConfiguration = new ParserConfiguration();
    static JavaParser parser;

    final static String CODE_GENERATED = "Code generated by Microsoft (R) AutoRest Code Generator.";

    public static void main(String[] args) throws Exception {
        Path projectDir = Path.of("C:\\github\\azure-java_vnext\\azure-mgmt-storage\\src");
        Path srcDir = Path.of("C:\\github\\azure-java_vnext\\azure-mgmt-resources\\src\\main\\java");
        Path resourcesSrcDir = Path.of("C:\\github\\azure-java_vnext\\azure-mgmt-storage\\src\\main\\java");

        combinedTypeSolver.add(new ReflectionTypeSolver(false));
        combinedTypeSolver.add(new JavaParserTypeSolver(srcDir));
        combinedTypeSolver.add(new JavaParserTypeSolver(resourcesSrcDir));

        symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        parserFacade = JavaParserFacade.get(combinedTypeSolver);

        parserConfiguration = new ParserConfiguration();
        parserConfiguration.setSymbolResolver(symbolSolver);

        parser = new JavaParser(parserConfiguration);
        parser = new JavaParser();

        Files.walk(projectDir)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }

    private static void processJavaFile(Path file) {
        try {
            if (fileContains(file, CODE_GENERATED)) {
                return;
            }

            ParseResult<CompilationUnit> parseResultCompilationUnit = parser.parse(file);
            CompilationUnit compilationUnit = parseResultCompilationUnit.getResult().get();

            System.out.println(file.toString());

            VoidVisitor<Abort> visitor = new VoidVisitorAdapter<>() {
                @Override
                public void visit(MethodCallExpr n, Abort arg) {
                    if (arg.abort) {
                        return;
                    }

                    super.visit(n, arg);

                    if (!n.getName().getIdentifier().startsWith("get") && !n.getName().getIdentifier().startsWith("set")) {
                        return;
                    }

                    System.out.println(" " + n.getName().getIdentifier() + " : " + n.getBegin().get().line);
                    try {
                        SymbolReference<ResolvedMethodDeclaration> methodReference = parserFacade.solve(n);
                        if (methodReference.isSolved()) {
                            ResolvedMethodDeclaration resolvedMethodDeclaration = methodReference.getCorrespondingDeclaration();
                            System.out.println(" -> " + resolvedMethodDeclaration.getQualifiedSignature());
                        }
                    } catch (UnsolvedSymbolException e) {
                        System.out.println(" UnsolvedSymbolException " + e.getMessage());
                    } catch (StackOverflowError e) {
                        System.out.println(" StackOverflowError");
                        arg.abort = true;
                    }
                }
            };
            visitor.visit(compilationUnit, new Abort());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Abort {
        private boolean abort = false;
    }

    private static boolean fileContains(Path file, String text) throws IOException {
        final int checkLines = 10;
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile(), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.contains(text)) {
                    return true;
                }
                if (++count > checkLines) {
                    return false;
                }
            }
        }
        return false;
    }

    private static void writeToFile(File file, String text) throws IOException {
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(fw)) {
            out.write(text);
        }
    }
}
