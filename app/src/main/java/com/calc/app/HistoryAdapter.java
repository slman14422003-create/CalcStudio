package com.calc.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;

    public interface Listener {
        void onOpen(HistoryItem item);
        void onCopy(HistoryItem item);
    }

    private final Context ctx;
    private final List<Object> rows = new ArrayList<>();
    private final Listener listener;
    private final boolean thousands;

    public HistoryAdapter(Context ctx, List<HistoryItem> items, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        this.thousands = new Store(ctx).getThousands();
        int lastKey = -1;
        for (HistoryItem it : items) {
            int key = dayKey(it.time);
            if (key != lastKey) {
                rows.add(dayLabel(it.time));
                lastKey = key;
            }
            rows.add(it);
        }
    }

    private static int dayKey(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    private String dayLabel(long time) {
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        now.add(Calendar.DAY_OF_YEAR, -1);
        int yesterday = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        int key = dayKey(time);
        if (key == today) return ctx.getString(R.string.history_today);
        if (key == yesterday) return ctx.getString(R.string.history_yesterday);
        return DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH);
    }

    public HistoryItem itemAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Object o = rows.get(position);
        return o instanceof HistoryItem ? (HistoryItem) o : null;
    }

    public void removeAt(int position) {
        if (position < 0 || position >= rows.size()) return;
        rows.remove(position);
        notifyItemRemoved(position);
        int headerPos = position - 1;
        boolean prevIsHeader = headerPos >= 0 && rows.get(headerPos) instanceof String;
        boolean nextIsEnd = position >= rows.size() || rows.get(position) instanceof String;
        if (prevIsHeader && nextIsEnd) {
            rows.remove(headerPos);
            notifyItemRemoved(headerPos);
        }
    }

    public boolean isEmpty() { return rows.isEmpty(); }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            TextView t = new TextView(ctx);
            t.setTextSize(13);
            t.setAllCaps(false);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setTextColor(Ui.attrColor(ctx, R.attr.cTextSecondary));
            t.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 14), Ui.dp(ctx, 6), Ui.dp(ctx, 4));
            t.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new HeaderVH(t);
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setForeground(ctx.getDrawable(R.drawable.bg_row_ripple));
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 10), Ui.dp(ctx, 10), Ui.dp(ctx, 14));
        RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
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
        elp.setMarginEnd(Ui.dp(ctx, 8));
        top.addView(expr, elp);

        ImageButton copy = new ImageButton(ctx, null, 0, R.style.IconButton);
        copy.setImageResource(R.drawable.ic_copy);
        copy.setImageTintList(ColorStateList.valueOf(Ui.attrColor(ctx, R.attr.cTextSecondary)));
        copy.setContentDescription(ctx.getString(R.string.history_copy));
        top.addView(copy, new LinearLayout.LayoutParams(Ui.dp(ctx, 34), Ui.dp(ctx, 34)));
        row.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView result = new TextView(ctx);
        result.setTextSize(28);
        result.setSingleLine(true);
        result.setEllipsize(TextUtils.TruncateAt.START);
        result.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        result.setTextColor(Ui.attrColor(ctx, R.attr.cTextPrimary));
        result.setGravity(Gravity.END);
        LinearLayout.LayoutParams rsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rsLp.topMargin = Ui.dp(ctx, 2);
        rsLp.setMarginEnd(Ui.dp(ctx, 8));
        row.addView(result, rsLp);

        return new ItemVH(row, time, expr, result, copy);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object o = rows.get(position);
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).label.setText((String) o);
            return;
        }
        final HistoryItem item = (HistoryItem) o;
        ItemVH h = (ItemVH) holder;
        h.expr.setText(Fmt.prettyExpr(item.expression, thousands && item.kind == HistoryItem.KIND_CALC));
        h.result.setText("= " + item.result);
        if (item.time > 0) {
            h.time.setText(DateUtils.formatDateTime(ctx, item.time, DateUtils.FORMAT_SHOW_TIME));
            h.time.setVisibility(View.VISIBLE);
        } else {
            h.time.setText("");
            h.time.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> listener.onOpen(item));
        h.copy.setOnClickListener(v -> listener.onCopy(item));
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static final class HeaderVH extends RecyclerView.ViewHolder {
        final TextView label;
        HeaderVH(TextView itemView) {
            super(itemView);
            this.label = itemView;
        }
    }

    static final class ItemVH extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView expr;
        final TextView result;
        final ImageButton copy;
        ItemVH(View itemView, TextView time, TextView expr, TextView result, ImageButton copy) {
            super(itemView);
            this.time = time;
            this.expr = expr;
            this.result = result;
            this.copy = copy;
        }
    }
}
