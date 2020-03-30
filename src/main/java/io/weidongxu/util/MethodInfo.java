package io.weidongxu.util;

import lombok.Value;

import java.nio.file.Path;

@Value
public class MethodInfo {
    private Path path;

    private int lineNumber;
    private String methodName;

    private String refClassName;
}
