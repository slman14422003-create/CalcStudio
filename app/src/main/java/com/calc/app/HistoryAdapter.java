package com.calc.app;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
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
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 14), Ui.dp(ctx, 18), Ui.dp(ctx, 14));
        RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5));
        row.setLayoutParams(rlp);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView time = new TextView(ctx);
        time.setTextSize(12);
        time.setSingleLine(true);
        time.setTextColor(Ui.attrColor(ctx, R.attr.cTextHint));
        top.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView expr = new TextView(ctx);
        expr.setTextSize(15);
        expr.setSingleLine(true);
        expr.setEllipsize(TextUtils.TruncateAt.START);
        expr.setTextColor(Ui.attrColor(ctx, R.attr.cTextSecondary));
        expr.setGravity(Gravity.END);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        elp.setMarginStart(Ui.dp(ctx, 12));
        top.addView(expr, elp);
        row.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView result = new TextView(ctx);
        result.setTextSize(28);
        result.setSingleLine(true);
        result.setEllipsize(TextUtils.TruncateAt.START);
        result.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        result.setTextColor(Ui.attrColor(ctx, R.attr.cTextPrimary));
        result.setGravity(Gravity.END);
        LinearLayout.LayoutParams rsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rsLp.topMargin = Ui.dp(ctx, 4);
        row.addView(result, rsLp);

        return new VH(row, time, expr, result);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HistoryItem item = items.get(position);
        h.expr.setText(item.expression);
        h.result.setText("= " + item.result);
        if (item.time > 0) {
            h.time.setText(DateUtils.formatDateTime(ctx, item.time,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL));
            h.time.setVisibility(View.VISIBLE);
        } else {
            h.time.setText("");
            h.time.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView expr;
        final TextView result;
        VH(View itemView, TextView time, TextView expr, TextView result) {
            super(itemView);
            this.time = time;
            this.expr = expr;
            this.result = result;
        }
    }
}
