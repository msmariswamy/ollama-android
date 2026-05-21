package com.ollama.mobile.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.attachment.AttachmentPicker;
import com.ollama.mobile.databinding.ActivityChatBinding;
import com.ollama.mobile.ui.model.ModelSelectorFragment;
import com.ollama.mobile.ui.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";

    private ActivityChatBinding binding;
    private ChatViewModel viewModel;
    private ChatMessageAdapter adapter;
    private AttachmentPicker attachmentPicker;
    private AttachmentChipAdapter attachmentChipAdapter;
    private SidebarController sidebarController;
    private View emptyChatView;
    private SpeechRecognizer speechRecognizer;
    private static final int[] MUTED_STREAMS = {
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_RING
    };
    private final int[] savedStreamVolumes = new int[MUTED_STREAMS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) { unmuteAudio(); }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
                @Override
                public void onResults(Bundle results) {
                    unmuteAudio();
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0).trim();
                        if (!text.isEmpty()) {
                            binding.etInput.setText(text);
                            binding.etInput.setSelection(text.length());
                            binding.etInput.requestFocus();
                        }
                    }
                }
            });
        }

        // Edge-to-edge: let the app draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // Push content below status bar; pad root bottom for nav bar / keyboard
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            binding.contentLayout.setPadding(
                    systemBars.left, systemBars.top, systemBars.right, bottomInset);
            if (ime.bottom > 0) {
                binding.rvMessages.post(() -> {
                    int count = adapter != null ? adapter.getItemCount() : 0;
                    if (count > 0) binding.rvMessages.scrollToPosition(count - 1);
                });
            }
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        adapter = new ChatMessageAdapter();
        adapter.setOnMessageLongPressListener((position, message) -> {
            new AlertDialog.Builder(this)
                    .setItems(new CharSequence[]{"Edit", "Copy"}, (dialog, which) -> {
                        if (which == 0) {
                            binding.etInput.setText(message.content);
                            binding.etInput.requestFocus();
                            binding.etInput.setSelection(binding.etInput.getText().length());
                            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            if (imm != null) imm.showSoftInput(binding.etInput, InputMethodManager.SHOW_IMPLICIT);
                            binding.btnSend.setOnClickListener(v -> {
                                String text = binding.etInput.getText() != null
                                        ? binding.etInput.getText().toString().trim() : "";
                                if (!text.isEmpty()) {
                                    binding.etInput.setText("");
                                    viewModel.editMessage(position, text);
                                }
                                binding.btnSend.setOnClickListener(normalSendListener());
                            });
                        } else {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("message", message.content));
                                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .show();
        });
        adapter.setOnRegenerateListener((position, message) ->
                viewModel.regenerateMessage(position));
        adapter.setOnEditListener((position, message) -> {
            binding.etInput.setText(message.content);
            binding.etInput.requestFocus();
            binding.etInput.setSelection(binding.etInput.getText().length());
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            binding.btnSend.setOnClickListener(v -> {
                String text = binding.etInput.getText() != null
                        ? binding.etInput.getText().toString().trim() : "";
                if (!text.isEmpty()) {
                    binding.etInput.setText("");
                    viewModel.editMessage(position, text);
                }
                binding.btnSend.setOnClickListener(normalSendListener());
            });
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);

        // Set up attachment preview recycler
        attachmentChipAdapter = new AttachmentChipAdapter(true);
        attachmentChipAdapter.setRemoveListener(position -> viewModel.removeAttachment(position));
        LinearLayoutManager chipLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.attachmentPreviewRecycler.setLayoutManager(chipLayoutManager);
        binding.attachmentPreviewRecycler.setAdapter(attachmentChipAdapter);

        // Set up attachment picker (must be registered before activity starts)
        attachmentPicker = new AttachmentPicker(this, uris -> viewModel.addAttachments(this, uris));

        long convId = getIntent().getLongExtra(EXTRA_CONVERSATION_ID, -1);
        if (convId != -1) {
            viewModel.loadConversation(convId);
        }

        observeViewModel();
        setupInputRow();
        setupModelSelector();
        setupTitleBar();
        setupSidebar();
        setupEmptyState();
    }

    private void observeViewModel() {
        viewModel.messages.observe(this, messages -> {
            adapter.setMessages(messages);
            if (!messages.isEmpty()) {
                binding.rvMessages.scrollToPosition(messages.size() - 1);
            }
        });

        viewModel.isStreaming.observe(this, streaming -> {
            binding.btnSend.setVisibility(streaming ? View.GONE : View.VISIBLE);
            binding.btnStop.setVisibility(streaming ? View.VISIBLE : View.GONE);
            binding.etInput.setEnabled(!streaming);
        });

        viewModel.errorEvent.observe(this, error -> {
            if ("no_model".equals(error)) {
                Snackbar.make(binding.getRoot(), R.string.no_model_selected, Snackbar.LENGTH_LONG)
                        .setAction(R.string.select_model, v -> openModelSelector())
                        .show();
            } else if (error != null) {
                Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
            }
        });

        viewModel.selectedModel.observe(this, model -> {
            binding.tvModelName.setText(model != null && !model.isEmpty() ? model : getString(R.string.select_model));
        });

        viewModel.conversationTitle.observe(this, title -> {
            if (title != null && !title.isEmpty()) {
                binding.tvConversationTitle.setText(title);
            }
        });

        viewModel.pendingAttachments.observe(this, attachments -> {
            boolean hasAttachments = attachments != null && !attachments.isEmpty();
            binding.attachmentPreviewRecycler.setVisibility(hasAttachments ? View.VISIBLE : View.GONE);
            attachmentChipAdapter.setAttachments(attachments);
        });

        viewModel.isProcessingAttachments.observe(this, processing -> {
            binding.btnSend.setEnabled(!Boolean.TRUE.equals(processing));
        });

        viewModel.tokensPerSecond.observe(this, tps -> {
            if (tps == null) {
                stopDotAnimation();
                binding.streamingStatusRow.setVisibility(View.GONE);
            } else {
                binding.streamingStatusRow.setVisibility(View.VISIBLE);
                binding.tvTokenRate.setText("generating · " + tps + " tok/s");
                startDotAnimation();
            }
        });
    }

    private android.animation.AnimatorSet dotAnimatorSet;

    private void startDotAnimation() {
        if (dotAnimatorSet != null && dotAnimatorSet.isRunning()) return;
        android.view.View[] dots = {binding.dot1, binding.dot2, binding.dot3};
        List<android.animation.Animator> anims = new ArrayList<>();
        for (int i = 0; i < dots.length; i++) {
            android.animation.ObjectAnimator anim = android.animation.ObjectAnimator.ofFloat(
                    dots[i], "alpha", 0.3f, 1f, 0.3f);
            anim.setDuration(900);
            anim.setStartDelay(i * 200L);
            anim.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            anims.add(anim);
        }
        dotAnimatorSet = new android.animation.AnimatorSet();
        dotAnimatorSet.playTogether(anims);
        dotAnimatorSet.start();
    }

    private void stopDotAnimation() {
        if (dotAnimatorSet != null) {
            dotAnimatorSet.cancel();
            dotAnimatorSet = null;
        }
    }

    private View.OnClickListener normalSendListener() {
        return v -> {
            String text = binding.etInput.getText() != null ? binding.etInput.getText().toString().trim() : "";
            if (!text.isEmpty() || (viewModel.pendingAttachments.getValue() != null
                    && !viewModel.pendingAttachments.getValue().isEmpty())) {
                binding.etInput.setText("");
                viewModel.sendMessage(text);
            }
        };
    }

    private void setupInputRow() {
        binding.btnAttach.setOnClickListener(v -> attachmentPicker.showChooserDialog());
        binding.btnSend.setOnClickListener(normalSendListener());
        binding.btnStop.setOnClickListener(v -> viewModel.stopStream());
        binding.btnMic.setOnClickListener(v -> startVoiceInput());
    }

    private void startVoiceInput() {
        if (speechRecognizer == null) {
            Toast.makeText(this, "Speech recognition not available on this device",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        muteAudio();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    private void muteAudio() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        for (int i = 0; i < MUTED_STREAMS.length; i++) {
            savedStreamVolumes[i] = am.getStreamVolume(MUTED_STREAMS[i]);
            am.setStreamVolume(MUTED_STREAMS[i], 0, 0);
        }
    }

    private void unmuteAudio() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        for (int i = 0; i < MUTED_STREAMS.length; i++) {
            am.setStreamVolume(MUTED_STREAMS[i], savedStreamVolumes[i], 0);
        }
    }

    private void setupModelSelector() {
        binding.modelSelectorContainer.setOnClickListener(v -> openModelSelector());
    }

    private void setupTitleBar() {
        // Tap title text or pencil icon → enter edit mode
        View.OnClickListener enterEdit = v -> {
            String current = binding.tvConversationTitle.getText().toString();
            binding.etConversationTitle.setText(current);
            binding.tvConversationTitle.setVisibility(View.GONE);
            binding.ivEditTitle.setVisibility(View.GONE);
            binding.etConversationTitle.setVisibility(View.VISIBLE);
            binding.ivSaveTitle.setVisibility(View.VISIBLE);
            binding.etConversationTitle.selectAll();
            binding.etConversationTitle.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.etConversationTitle, InputMethodManager.SHOW_IMPLICIT);
        };
        binding.tvConversationTitle.setOnClickListener(enterEdit);
        binding.ivEditTitle.setOnClickListener(enterEdit);

        // Save button — explicit tap to commit
        binding.ivSaveTitle.setOnClickListener(v -> saveTitle());

        // IME Done key also saves
        binding.etConversationTitle.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveTitle();
                return true;
            }
            return false;
        });

        // Focus lost without save → revert
        binding.etConversationTitle.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && binding.etConversationTitle.getVisibility() == View.VISIBLE) {
                exitTitleEdit(false);
            }
        });
    }

    private void saveTitle() {
        String newTitle = binding.etConversationTitle.getText().toString().trim();
        if (!newTitle.isEmpty()) {
            viewModel.updateConversationTitle(newTitle);
        }
        exitTitleEdit(true);
    }

    private void exitTitleEdit(boolean hideKeyboard) {
        binding.etConversationTitle.setVisibility(View.GONE);
        binding.ivSaveTitle.setVisibility(View.GONE);
        binding.tvConversationTitle.setVisibility(View.VISIBLE);
        binding.ivEditTitle.setVisibility(View.VISIBLE);
        if (hideKeyboard) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(binding.etConversationTitle.getWindowToken(), 0);
        }
    }

    private void openModelSelector() {
        ModelSelectorFragment fragment = ModelSelectorFragment.newInstance();
        fragment.setOnModelSelectedListener(model -> viewModel.refreshSelectedModel());
        fragment.show(getSupportFragmentManager(), "model_selector");
    }

    private void setupSidebar() {
        DrawerLayout drawerLayout = binding.drawerLayout;
        View sidebarView = binding.sidebarView.getRoot();
        sidebarController = new SidebarController(
                this, drawerLayout, sidebarView, viewModel,
                conversationId -> viewModel.loadConversation(conversationId));
    }

    private void setupEmptyState() {
        ViewStub stub = binding.stubEmptyChat;
        // Inflate lazily; observe messages to toggle visibility
        viewModel.messages.observe(this, messages -> {
            if (messages == null || messages.isEmpty()) {
                if (emptyChatView == null) {
                    emptyChatView = stub.inflate();
                    // Wire suggestion chips
                    String[] suggestions = {
                        "Brainstorm a name for…",
                        "Explain this regex to me",
                        "Describe this image",
                        "Rewrite this paragraph"
                    };
                    int[] chipIds = {R.id.chip0, R.id.chip1, R.id.chip2, R.id.chip3};
                    for (int i = 0; i < chipIds.length; i++) {
                        final String suggestion = suggestions[i];
                        TextView chip = emptyChatView.findViewById(chipIds[i]);
                        if (chip != null) {
                            chip.setOnClickListener(v -> {
                                binding.etInput.setText("");
                                viewModel.sendMessage(suggestion);
                            });
                        }
                    }
                }
                emptyChatView.setVisibility(View.VISIBLE);
                binding.rvMessages.setVisibility(View.GONE);
            } else {
                if (emptyChatView != null) emptyChatView.setVisibility(View.GONE);
                binding.rvMessages.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        viewModel.systemPrompt.observe(this, prompt -> {
            MenuItem item = menu.findItem(R.id.action_system_prompt);
            if (item != null) {
                String title = (prompt != null && !prompt.trim().isEmpty())
                        ? "System Prompt ●" : "System Prompt";
                item.setTitle(title);
            }
        });
        viewModel.messages.observe(this, msgs -> {
            MenuItem exportItem = menu.findItem(R.id.action_export);
            if (exportItem != null) {
                exportItem.setEnabled(msgs != null && !msgs.isEmpty());
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_sidebar) {
            if (sidebarController != null) sidebarController.openDrawer();
            return true;
        }
        if (item.getItemId() == R.id.action_search) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_system_prompt) {
            openSystemPromptEditor();
            return true;
        }
        if (item.getItemId() == R.id.action_export) {
            showExportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showExportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Export Conversation")
                .setItems(new String[]{"Markdown (.md)", "JSON (.json)"}, (dialog, which) -> {
                    List<UiMessage> msgs = viewModel.messages.getValue();
                    if (msgs == null || msgs.isEmpty()) return;
                    try {
                        String content;
                        String fileName;
                        String mimeType;
                        if (which == 0) {
                            content = ConversationExporter.toMarkdown(msgs);
                            fileName = "conversation.md";
                            mimeType = "text/markdown";
                        } else {
                            content = ConversationExporter.toJson(msgs);
                            fileName = "conversation.json";
                            mimeType = "application/json";
                        }
                        java.io.File exportDir = new java.io.File(getCacheDir(), "exports");
                        exportDir.mkdirs();
                        java.io.File file = new java.io.File(exportDir, fileName);
                        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                            writer.write(content);
                        }
                        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                this, getPackageName() + ".fileprovider", file);
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType(mimeType);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(shareIntent, "Export conversation"));
                    } catch (Exception e) {
                        Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void openSystemPromptEditor() {
        long convId = viewModel.getConversationId();
        String current = viewModel.systemPrompt.getValue();
        SystemPromptBottomSheet sheet = SystemPromptBottomSheet.newInstance(current);
        sheet.setOnSaveListener(prompt -> {
            viewModel.systemPrompt.setValue(prompt);
            if (convId != -1) {
                ((com.ollama.mobile.OllamaApp) getApplication())
                        .getAppContainer().conversationRepository
                        .setSystemPrompt(convId, prompt);
            }
        });
        sheet.show(getSupportFragmentManager(), "system_prompt");
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refreshSelectedModel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
