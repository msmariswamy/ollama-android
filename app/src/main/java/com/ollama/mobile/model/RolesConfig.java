package com.ollama.mobile.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RolesConfig {

    public RoleEntry java;
    public RoleEntry android;
    public RoleEntry ios;
    public RoleEntry reactnative;

    public static class RoleEntry {
        public String title;
        public String description;

        @SerializedName("technical_skills")
        public List<String> technicalSkills;
    }
}
