package androidx.recyclerview.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
public class RecyclerView extends ViewGroup {
    public RecyclerView(Context context) { super(context); }
    public RecyclerView(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {}
    public void setAdapter(Adapter<?> adapter) {}
    public Adapter<?> getAdapter() { return null; }
    public void setLayoutManager(LayoutManager layout) {}
    public LayoutManager getLayoutManager() { return null; }
    public void setHasFixedSize(boolean hasFixedSize) {}
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
    }
    public static abstract class LayoutManager {}
    public static abstract class ViewHolder {
        public final View itemView;
        public ViewHolder(View itemView) { this.itemView = itemView; }
    }
    public static abstract class Adapter<VH extends ViewHolder> {
        public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);
        public abstract void onBindViewHolder(VH holder, int position);
        public abstract int getItemCount();
        public int getItemViewType(int position) { return 0; }
        public long getItemId(int position) { return -1; }
        public final void notifyDataSetChanged() {}
        public final void notifyItemChanged(int position) {}
        public final void notifyItemInserted(int position) {}
        public final void notifyItemRemoved(int position) {}
    }
}
