package com.ollama.mobile.model;

import java.util.List;

public class InterviewRole {
    public final String id;
    public final String title;
    public final String description;
    public final List<String> technicalSkills;

    public InterviewRole(String id, String title, String description, List<String> technicalSkills) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.technicalSkills = technicalSkills;
    }

    public static final InterviewRole CUSTOM = new InterviewRole("custom", "Custom", "", null);
}
