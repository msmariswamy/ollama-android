package com.ollama.mobile.ui.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.ollama.mobile.R;

public class SystemPromptBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PROMPT = "current_prompt";

    private OnSaveListener saveListener;

    public interface OnSaveListener {
        void onSave(String prompt);
    }

    public static SystemPromptBottomSheet newInstance(String currentPrompt) {
        SystemPromptBottomSheet sheet = new SystemPromptBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PROMPT, currentPrompt != null ? currentPrompt : "");
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnSaveListener(OnSaveListener listener) {
        this.saveListener = listener;
    }

    private String getPresetText(int chipId) {
        if (chipId == R.id.chipDefault) return "";
        if (chipId == R.id.chipConcise) return "You are a helpful assistant. Respond concisely and directly. Avoid unnecessary preamble or filler.";
        if (chipId == R.id.chipCodeReviewer) return "You are an expert code reviewer. Review code for correctness, security, performance, and maintainability. Be direct and specific.";
        if (chipId == R.id.chipTutor) return "You are a patient and encouraging tutor. Explain concepts clearly with examples. Check for understanding and adapt to the learner's level.";
        if (chipId == R.id.chipEli5) return "Explain everything as if you're talking to a 5-year-old. Use simple words, analogies, and short sentences.";
        if (chipId == R.id.chipStepByStep) return "When answering questions, always break down your response into clear numbered steps. Think through problems methodically.";
        return "";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_system_prompt, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etPrompt = view.findViewById(R.id.etSystemPrompt);
        TextView tvCharCount = view.findViewById(R.id.tvCharCount);
        ChipGroup chipGroupPresets = view.findViewById(R.id.chipGroupPresets);

        String current = getArguments() != null ? getArguments().getString(ARG_PROMPT, "") : "";
        etPrompt.setText(current);
        tvCharCount.setText(current.length() + " characters");

        etPrompt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                tvCharCount.setText(s.length() + " characters");
            }
        });

        chipGroupPresets.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String preset = getPresetText(id);
            etPrompt.setText(preset);
            etPrompt.setSelection(preset.length());
        });

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String prompt = etPrompt.getText() != null ? etPrompt.getText().toString().trim() : "";
            if (saveListener != null) saveListener.onSave(prompt);
            dismiss();
        });
    }
}
