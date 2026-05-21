package com.ollama.mobile.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;
import com.ollama.mobile.ui.search.SearchResultAdapter;
import com.ollama.mobile.ui.search.SearchViewModel;

public class SearchActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        View root = findViewById(R.id.searchRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        SearchViewModel viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        SearchResultAdapter adapter = new SearchResultAdapter();

        RecyclerView rv = findViewById(R.id.rvSearchResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        EditText etSearch = findViewById(R.id.etSearch);
        TextView tvEmpty = findViewById(R.id.tvEmptySearch);

        adapter.setOnResultClickListener(convId -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, convId);
            startActivity(intent);
            finish();
        });

        viewModel.results.observe(this, results -> {
            String query = etSearch.getText() != null ? etSearch.getText().toString() : "";
            if (results == null || results.isEmpty()) {
                boolean hasQuery = query.trim().length() >= 2;
                tvEmpty.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
                tvEmpty.setText("No results for \"" + query.trim() + "\"");
                adapter.setResults(null, query);
            } else {
                tvEmpty.setVisibility(View.GONE);
                adapter.setResults(results, query);
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.search(s.toString());
            }
        });

        // Auto-focus keyboard
        etSearch.requestFocus();

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
    }
}
