package io.weidongxu.util;

import lombok.Data;

import java.util.List;

@Data
public class Configure {
    private String procedure;

    private String projectLocation;
    private List<String> additionalProjects;
    private List<String> referenceProjects;
}
