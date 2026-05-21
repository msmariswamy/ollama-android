package com.ollama.mobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ollama.mobile.R;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.model.RolesConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InterviewRoleRepository {

    private static final String TAG = "InterviewRoleRepository";
    private static final String PREFS_NAME = "interview_role_prefs";
    private static final String KEY_CUSTOM_ROLES = "custom_roles";
    private static final String KEY_PROMPT_PREFIX = "prompt_";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public InterviewRoleRepository(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Load all roles: built-ins from R.raw.roles_config + user-created from prefs.
     * Sets role.systemPrompt from saved overrides or default.
     */
    public List<InterviewRole> loadAllRoles(Context context) {
        List<InterviewRole> roles = new ArrayList<>();

        // Load built-in roles
        try (InputStream is = context.getResources().openRawResource(R.raw.roles_config);
             InputStreamReader reader = new InputStreamReader(is)) {
            RolesConfig config = gson.fromJson(reader, RolesConfig.class);
            if (config.java != null) {
                InterviewRole r = new InterviewRole("java", config.java.title, config.java.description, config.java.technicalSkills);
                r.systemPrompt = getSystemPrompt(r);
                roles.add(r);
            }
            if (config.android != null) {
                InterviewRole r = new InterviewRole("android", config.android.title, config.android.description, config.android.technicalSkills);
                r.systemPrompt = getSystemPrompt(r);
                roles.add(r);
            }
            if (config.ios != null) {
                InterviewRole r = new InterviewRole("ios", config.ios.title, config.ios.description, config.ios.technicalSkills);
                r.systemPrompt = getSystemPrompt(r);
                roles.add(r);
            }
            if (config.reactnative != null) {
                InterviewRole r = new InterviewRole("reactnative", config.reactnative.title, config.reactnative.description, config.reactnative.technicalSkills);
                r.systemPrompt = getSystemPrompt(r);
                roles.add(r);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse roles_config.json", e);
        }

        // Load user-created roles
        roles.addAll(loadUserRoles());

        return roles;
    }

    private List<InterviewRole> loadUserRoles() {
        String json = prefs.getString(KEY_CUSTOM_ROLES, "[]");
        List<InterviewRole> userRoles = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String id = obj.get("id").getAsString();
                String title = obj.get("title").getAsString();
                String description = obj.has("description") ? obj.get("description").getAsString() : "";
                List<String> skills = null;
                if (obj.has("technicalSkills") && !obj.get("technicalSkills").isJsonNull()) {
                    skills = new ArrayList<>();
                    for (JsonElement skill : obj.getAsJsonArray("technicalSkills")) {
                        skills.add(skill.getAsString());
                    }
                }
                InterviewRole role = new InterviewRole(id, title, description, skills);
                role.systemPrompt = getSystemPrompt(role);
                userRoles.add(role);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse custom_roles", e);
        }
        return userRoles;
    }

    /**
     * Returns saved prompt override for the role, or the default prompt.
     */
    public String getSystemPrompt(InterviewRole role) {
        String saved = prefs.getString(KEY_PROMPT_PREFIX + role.id, null);
        if (saved != null && !saved.isEmpty()) return saved;
        return buildDefaultPrompt(role);
    }

    /**
     * Saves a system prompt override for the given role ID.
     */
    public void saveSystemPrompt(String roleId, String prompt) {
        prefs.edit().putString(KEY_PROMPT_PREFIX + roleId, prompt).apply();
    }

    /**
     * Appends or updates a user-created role in the custom_roles JSON array.
     */
    public void saveUserRole(InterviewRole role) {
        String json = prefs.getString(KEY_CUSTOM_ROLES, "[]");
        JsonArray arr;
        try {
            arr = JsonParser.parseString(json).getAsJsonArray();
        } catch (Exception e) {
            arr = new JsonArray();
        }

        // Remove existing entry with the same id
        JsonArray updated = new JsonArray();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            if (!obj.get("id").getAsString().equals(role.id)) {
                updated.add(obj);
            }
        }

        // Add the role
        JsonObject obj = new JsonObject();
        obj.addProperty("id", role.id);
        obj.addProperty("title", role.title);
        obj.addProperty("description", role.description != null ? role.description : "");
        if (role.technicalSkills != null && !role.technicalSkills.isEmpty()) {
            JsonArray skills = new JsonArray();
            for (String s : role.technicalSkills) skills.add(s);
            obj.add("technicalSkills", skills);
        }
        updated.add(obj);

        prefs.edit().putString(KEY_CUSTOM_ROLES, updated.toString()).apply();
    }

    /**
     * Removes a user-created role by ID.
     */
    public void deleteUserRole(String roleId) {
        String json = prefs.getString(KEY_CUSTOM_ROLES, "[]");
        JsonArray arr;
        try {
            arr = JsonParser.parseString(json).getAsJsonArray();
        } catch (Exception e) {
            return;
        }

        JsonArray updated = new JsonArray();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            if (!obj.get("id").getAsString().equals(roleId)) {
                updated.add(obj);
            }
        }

        prefs.edit().putString(KEY_CUSTOM_ROLES, updated.toString()).apply();
        prefs.edit().remove(KEY_PROMPT_PREFIX + roleId).apply();
    }

    /**
     * Returns true if the role is user-created (id starts with "user_").
     */
    public boolean isUserCreatedRole(String roleId) {
        return roleId != null && roleId.startsWith("user_");
    }

    /**
     * Builds the default system prompt string from role fields.
     * Same content as the hardcoded prompt in InterviewRepository, minus the transcript section.
     */
    public static String buildDefaultPrompt(InterviewRole role) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert technical interviewer observing a real-time technical interview.\n");
        sb.append("Role being evaluated: ").append(role.title).append("\n");
        sb.append("Role description: ").append(role.description != null ? role.description : "");
        if (role.technicalSkills != null && !role.technicalSkills.isEmpty()) {
            sb.append("\nTechnical skills expected: ").append(String.join(", ", role.technicalSkills));
        }
        sb.append("\n\nRespond in under 150 words with:\n");
        sb.append("Current Topic: <topic being discussed>\n");
        sb.append("Follow-up Probes: <2-3 follow-up questions to ask>\n");
        sb.append("Gaps Alert: <important areas not yet covered>");
        return sb.toString();
    }
}
