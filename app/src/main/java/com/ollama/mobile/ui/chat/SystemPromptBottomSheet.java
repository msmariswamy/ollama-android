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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_system_prompt, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etPrompt = view.findViewById(R.id.etSystemPrompt);
        TextView tvCharCount = view.findViewById(R.id.tvCharCount);

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

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String prompt = etPrompt.getText() != null ? etPrompt.getText().toString().trim() : "";
            if (saveListener != null) saveListener.onSave(prompt);
            dismiss();
        });
    }
}
