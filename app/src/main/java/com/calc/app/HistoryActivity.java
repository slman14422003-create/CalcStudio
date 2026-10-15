package com.calc.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public final class HistoryActivity extends AppCompatActivity {

    private Store store;
    private RecyclerView rv;
    private View empty;
    private TextView clear;

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
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 4));

        ImageButton back = new ImageButton(this, null, 0, R.style.IconButton);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription(getString(R.string.action_back));
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(spacer);

        clear = new TextView(this);
        clear.setText(R.string.history_clear);
        clear.setTextSize(15);
        clear.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        clear.setTextColor(getColor(R.color.bad));
        clear.setGravity(Gravity.CENTER);
        clear.setBackgroundResource(R.drawable.bg_pill);
        clear.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        clear.setClickable(true);
        clear.setFocusable(true);
        clear.setOnClickListener(v -> {
            store.clearHistory();
            reload();
        });
        bar.addView(clear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36)));

        root.addView(bar);
        root.addView(Ui.largeTitle(this, R.string.history_title));

        LinearLayout emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 64));

        ImageView emptyIcon = new ImageView(this);
        emptyIcon.setImageResource(R.drawable.ic_history);
        emptyIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.attrColor(this, R.attr.cTextHint)));
        emptyIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        emptyBox.addView(emptyIcon, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));

        TextView emptyText = new TextView(this);
        emptyText.setText(R.string.history_empty);
        emptyText.setTextColor(Ui.attrColor(this, R.attr.cTextSecondary));
        emptyText.setTextSize(17);
        emptyText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = Ui.dp(this, 12);
        emptyBox.addView(emptyText, etLp);

        empty = emptyBox;
        root.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setClipToPadding(false);
        rv.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        rv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(rv);

        setContentView(root);
        reload();
    }

    private void reload() {
        List<HistoryItem> items = store.getHistory();
        boolean none = items.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        rv.setVisibility(none ? View.GONE : View.VISIBLE);
        clear.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
        rv.setAdapter(new HistoryAdapter(this, items, item -> {
            Intent data = new Intent();
            data.putExtra(MainActivity.EXTRA_RESTORE, item.expression);
            setResult(RESULT_OK, data);
            finish();
        }));
    }
}
