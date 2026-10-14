package com.calc.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
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
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), 0);

        ImageButton back = new ImageButton(this, null, 0, R.style.IconButton);
        back.setImageResource(R.drawable.ic_back);
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(spacer);

        TextView clear = new TextView(this);
        clear.setText(R.string.history_clear);
        clear.setTextSize(17);
        clear.setTextColor(Ui.attrColor(this, R.attr.cAccentText));
        clear.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
        clear.setOnClickListener(v -> {
            store.clearHistory();
            reload();
        });
        bar.addView(clear);

        root.addView(bar);
        root.addView(Ui.largeTitle(this, R.string.history_title));

        empty = new TextView(this);
        empty.setText(R.string.history_empty);
        empty.setTextColor(Ui.attrColor(this, R.attr.cTextSecondary));
        empty.setTextSize(17);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(this, 60), 0, 0);
        root.addView(empty);

        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setClipToPadding(false);
        rv.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 20));
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
