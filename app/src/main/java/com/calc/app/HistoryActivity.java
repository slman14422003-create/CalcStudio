package com.calc.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public final class HistoryActivity extends AppCompatActivity {

    private Store store;
    private RecyclerView rv;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.attrColor(this, R.attr.cBg));
        root.setFitsSystemWindows(true);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 14);
        bar.setPadding(pad, pad, pad, pad);

        Button back = new Button(this);
        back.setText("←");
        back.setMinWidth(0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText(R.string.history_title);
        title.setTextSize(18);
        title.setTextColor(Ui.attrColor(this, R.attr.cTextPrimary));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(Ui.dp(this, 10));
        title.setLayoutParams(tLp);
        bar.addView(title);

        Button clear = new Button(this);
        clear.setText(R.string.history_clear);
        clear.setAllCaps(false);
        clear.setTextSize(13);
        clear.setOnClickListener(v -> {
            store.clearHistory();
            reload();
        });
        bar.addView(clear);

        root.addView(bar);

        empty = new TextView(this);
        empty.setText(R.string.history_empty);
        empty.setTextColor(Ui.attrColor(this, R.attr.cTextSecondary));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(this, 60), 0, 0);
        root.addView(empty);

        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(rv);

        setContentView(root);
        reload();
    }

    private void reload() {
        List<HistoryItem> items = store.getHistory();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        rv.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setAdapter(new HistoryAdapter(this, items, item -> {
            Intent data = new Intent();
            data.putExtra(MainActivity.EXTRA_RESTORE, item.expression);
            setResult(RESULT_OK, data);
            finish();
        }));
    }
}
