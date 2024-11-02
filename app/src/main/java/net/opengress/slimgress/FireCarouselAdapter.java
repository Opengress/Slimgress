package net.opengress.slimgress;

import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getImageForCubeLevel;
import static net.opengress.slimgress.ViewHelpers.getImageForResoLevel;
import static net.opengress.slimgress.ViewHelpers.getImageForUltrastrikeLevel;
import static net.opengress.slimgress.ViewHelpers.getImageForXMPLevel;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FireCarouselAdapter extends RecyclerView.Adapter<FireCarouselAdapter.ViewHolder> {
    Context mContext;
    List<InventoryListItem> mArrayList;
    OnItemClickListener mOnClickListener;

    private int mSelectedPosition = RecyclerView.NO_POSITION;

    public FireCarouselAdapter(Context context, List<InventoryListItem> arrayList) {
        this.mContext = context;
        this.mArrayList = arrayList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.carousel_item, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        var item = mArrayList.get(position);
//        Glide .with(mContext).load(item).into(holder.imageView);
        switch (item.getType()) {
            case WeaponXMP ->
                    holder.imageView.setImageResource(getImageForXMPLevel(item.getLevel()));
            case WeaponUltraStrike ->
                    holder.imageView.setImageResource(getImageForUltrastrikeLevel(item.getLevel()));
            case PowerCube ->
                    holder.imageView.setImageResource(getImageForCubeLevel(item.getLevel()));
            case Resonator ->
                    holder.imageView.setImageResource(getImageForResoLevel(item.getLevel()));
            case FlipCard -> {
                switch (item.getFlipCardType()) {
                    case Ada -> holder.imageView.setImageResource(R.drawable.ada);
                    case Jarvis -> holder.imageView.setImageResource(R.drawable.jarvis);
                }
            }
        }
        holder.imageView.setContentDescription(String.format("L%d %s x%d", item.getLevel(), item.getDescription(), item.getQuantity()));
        holder.textView1.setText(String.format("x%d", item.getQuantity()));
        int level = item.getLevel();
        if (level > -1) {
            holder.textView2.setText(String.format("L%d", item.getLevel()));
        } else {
            holder.textView2.setText("");
        }

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setStroke(4, getColourFromResources(mContext.getResources(), getLevelColour(item.getLevel())));
        drawable.setCornerRadius(8);
        drawable.setAlpha(position == mSelectedPosition ? 255 : 0);
        holder.itemView.setBackground(drawable);


        holder.itemView.setOnClickListener(view -> {
            int previousPosition = mSelectedPosition;
            mSelectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPosition);
            notifyItemChanged(mSelectedPosition);
            mOnClickListener.onClick(holder.imageView, item);
        });
    }

    @Override
    public int getItemCount() {
        return mArrayList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView1, textView2;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.fire_carousel_image_view);
            textView1 = itemView.findViewById(R.id.fire_carousel_quantity);
            textView2 = itemView.findViewById(R.id.fire_carousel_level);
        }
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.mOnClickListener = onItemClickListener;
    }

    public interface OnItemClickListener {
        void onClick(ImageView imageView, InventoryListItem item);
    }

    public void removeItem(int position) {
        mArrayList.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, mArrayList.size());
    }

    public void setSelectedPosition(int index) {
        int previousPosition = mSelectedPosition;
        mSelectedPosition = index;
        notifyItemChanged(previousPosition);
        notifyItemChanged(mSelectedPosition);
    }
}