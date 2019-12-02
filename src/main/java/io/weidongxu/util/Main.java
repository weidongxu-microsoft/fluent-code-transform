package io.weidongxu.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        final String filename = "C:\\github_fork\\autorest.java\\tests\\src\\test\\java\\fixtures\\bodyfile\\FilesTests.java";
        final String[] segs = filename.split(Pattern.quote("\\"));
        final String className = segs[segs.length - 1].split(Pattern.quote("."))[0];

        CompilationUnit compilationUnit = StaticJavaParser.parse(new File(filename));

        // delete import e.g. AutoRestRFC1123DateTimeTestServiceImpl
        compilationUnit.getImports().removeIf(importDeclaration -> importDeclaration.getName().getIdentifier().contains("ServiceImpl"));

        // replace e.g. new AutoRestRFC1123DateTimeTestServiceImpl() -> new AutoRestRFC1123DateTimeTestServiceBuilder().build()
        compilationUnit.getClassByName(className).get().getMethods().stream()
                .filter(method -> method.getAnnotations().stream().anyMatch(expr -> expr.getName().getIdentifier().equals("BeforeClass")))
                .forEach(method -> method.getBody().get().getStatements().stream().forEach(stmt -> {
                    stmt.accept(new GenericVisitorAdapter<Node, Void>() {
                        @Override
                        public Node visit(AssignExpr n, Void arg) {
                            Expression expr = n.getValue();
                            if (expr.isObjectCreationExpr()) {
                                ObjectCreationExpr objectCreationExpr = expr.asObjectCreationExpr();
                                String exprStr = objectCreationExpr.getType().asString();
                                if (exprStr.contains("ServiceImpl")) {
                                    String newExprStr = "new " + exprStr.replace("Impl", "Builder") + "().build()";
                                    Expression newExpr = StaticJavaParser.parseExpression(newExprStr);
                                    n.setValue(newExpr);
                                }
                            }
                            return super.visit(n, arg);
                        }
                    }, null);
                }));

        // replace e.g. client.datetimerfc1123s().getInvalid() -> client.datetimerfc1123s().getInvalidWithResponseAsync().block().getValue();
        compilationUnit.getClassByName(className).get().getMethods().stream()
                .filter(method -> method.getAnnotations().stream().anyMatch(expr -> expr.getName().getIdentifier().equals("Test")))
                .forEach(method -> method.getBody().get().getStatements().stream().forEach(stmt -> {
                    stmt.accept(new GenericVisitorAdapter<Node, Void>() {
                        @Override
                        public Node visit(MethodCallExpr n, Void arg) {
                            if (n.getName().getIdentifier().startsWith("get") && !n.getName().getIdentifier().endsWith("Async") && n.getScope().isPresent() && n.getScope().get().toString().startsWith("client")) {
                                MethodCallExpr newCallExpr = StaticJavaParser.parseExpression(n.getScope().get().toString() + "." + n.getName().getIdentifier() + "WithResponseAsync().block().getValue()");
                                n.replace(newCallExpr);
                            }
                            if (n.getName().getIdentifier().startsWith("put") && !n.getName().getIdentifier().endsWith("Async") && n.getScope().isPresent() && n.getScope().get().toString().startsWith("client")) {
                                MethodCallExpr newCallExpr = StaticJavaParser.parseExpression(n.getScope().get().toString() + "." + n.getName().getIdentifier() + "WithResponseAsync(" + n.getArguments().stream().map(Expression::toString).collect(Collectors.joining(", ")) + ").block()");
                                n.replace(newCallExpr);
                            }
                            return super.visit(n, arg);
                        }
                    }, null);
                }));

        String code = compilationUnit.toString();
        writeToFile(new File(filename), code);
    }

    public static void writeToFile(File file, String text) throws IOException {
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(fw)) {
            out.write(text);
        }
    }
}
