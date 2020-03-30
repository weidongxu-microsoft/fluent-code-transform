package io.weidongxu.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Transform {

    private static final Logger logger = LoggerFactory.getLogger(Transform.class);

    private final Configure configure;

    private final Set<String> generatedClasses = new HashSet<>();

    private static final String CODE_GENERATED = "Code generated by Microsoft (R) AutoRest Code Generator.";

    public Transform(Configure configure) {
        this.configure = configure;
    }

    public void process() throws IOException {
        walkJavaFiles(Path.of(configure.getProjectLocation()), this::collectGeneratedClasses);

        JavaMethodCallParser parser = new JavaMethodCallParser(configure, Transform::fileIsGenerated);
        walkJavaFiles(Path.of(configure.getProjectLocation()), parser::processJavaFile);
        for (String additionalProject : configure.getAdditionalProjects()) {
            walkJavaFiles(Path.of(additionalProject), parser::processJavaFile);
        }

        List<MethodInfo> methodCalls = parser.getMethodCalls();
        Map<Path, List<MethodInfo>> methodCallsGroupedByPath = methodCalls.stream()
                .filter(c -> generatedClasses.contains(c.getRefClassName()))
                .collect(Collectors.groupingBy(MethodInfo::getPath));
        methodCallsGroupedByPath.forEach(this::replaceInFile);
    }

    private void collectGeneratedClasses(Path javaFile) {
        if (fileIsGenerated(javaFile)) {
            generatedClasses.add(javaFile.getFileName().toString().replace(".java", ""));
        }
    }

    private void walkJavaFiles(Path projectDir, Consumer<? super Path> action) throws IOException {
        Files.walk(projectDir)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .forEach(action);
    }

    private void replaceInFile(Path javaFile, List<MethodInfo> methodCalls) {
        if (methodCalls.isEmpty()) {
            return;
        }

        methodCalls.sort(Comparator.comparingInt(MethodInfo::getLineNumber));

        logger.info("Processing file: {}", javaFile.toString());

        try {
            List<String> lines = new ArrayList<>();
            try (FileReader fr = new FileReader(javaFile.toFile(), StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(fr)) {
                int lineNumber = 0;
                Iterator<MethodInfo> iterator = methodCalls.iterator();
                MethodInfo methodCall = iterator.next();

                String line;
                while ((line = br.readLine()) != null) {
                    if (methodCall != null && methodCall.getLineNumber() < lineNumber) {
                        if (iterator.hasNext()) {
                            methodCall = iterator.next();
                        } else {
                            methodCall = null;
                        }
                    }

                    while (methodCall != null && lineNumber == methodCall.getLineNumber()) {
                        String methodName = methodCall.getMethodName();
                        String replaceMethodName = methodName.startsWith("set")
                                ? "with" + methodName.substring(3)
                                : methodName.startsWith("is")
                                ? methodName.substring(2, 3).toLowerCase() + methodName.substring(3)
                                : methodName.substring(3, 4).toLowerCase() + methodName.substring(4);

                        line = line.replace(methodName, replaceMethodName);

                        if (iterator.hasNext()) {
                            methodCall = iterator.next();
                        } else {
                            methodCall = null;
                        }
                    }

                    lines.add(line);
                    ++lineNumber;
                }
            }

            writeToFile(javaFile, lines);
        } catch (IOException e) {
            //
        }
    }

    private static void writeToFile(Path file, String text) throws IOException {
        try (FileWriter fw = new FileWriter(file.toFile(), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(fw)) {
            out.write(text);
        }
    }

    public static void writeToFile(Path file, List<String> lines) throws IOException {
        try (FileWriter fw = new FileWriter(file.toFile(), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(fw)) {
            for (String line : lines) {
                out.write(line);
                out.write("\n");
            }
        }
    }

    private static boolean fileIsGenerated(Path javaFile) {
        final int checkLines = 10;
        try (BufferedReader reader = new BufferedReader(new FileReader(javaFile.toFile(), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.contains(CODE_GENERATED)) {
                    return true;
                }
                if (++count > checkLines) {
                    return false;
                }
            }
        } catch (IOException e) {
            //
        }
        return false;
    }
}