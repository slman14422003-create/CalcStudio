package com.calc.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    public interface OnItemClick { void onClick(HistoryItem item); }

    private final Context ctx;
    private final List<HistoryItem> items;
    private final OnItemClick listener;

    public HistoryAdapter(Context ctx, List<HistoryItem> items, OnItemClick listener) {
        this.ctx = ctx;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setForeground(ctx.getDrawable(R.drawable.bg_row_ripple));
        row.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12));
        RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
        row.setLayoutParams(rlp);

        TextView expr = new TextView(ctx);
        expr.setId(View.generateViewId());
        expr.setTextSize(15);
        expr.setTypeface(android.graphics.Typeface.MONOSPACE);
        expr.setTextColor(Ui.attrColor(ctx, R.attr.cTextSecondary));
        expr.setGravity(Gravity.END);
        row.addView(expr);

        TextView result = new TextView(ctx);
        result.setId(View.generateViewId());
        result.setTextSize(28);
        result.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        result.setTextColor(Ui.attrColor(ctx, R.attr.cTextPrimary));
        result.setGravity(Gravity.END);
        row.addView(result);

        return new VH(row, expr, result);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HistoryItem item = items.get(position);
        h.expr.setText(item.expression);
        h.result.setText("= " + item.result);
        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView expr;
        final TextView result;
        VH(View itemView, TextView expr, TextView result) {
            super(itemView);
            this.expr = expr;
            this.result = result;
        }
    }
}
