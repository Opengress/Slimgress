/*

 Slimgress: Opengress API for Android
 Copyright (C) 2013 Norman Link <norman.link@gmx.net>
 Copyright (C) 2024 Opengress Team <info@opengress.net>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */

package net.opengress.slimgress;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;

public class InventoryList extends BaseExpandableListAdapter
{
    public final ArrayList<String> mGroupItem;
    public ArrayList<InventoryListItem> mTempChild;
    public final ArrayList<Object> mChildItem;
    public LayoutInflater mInflater;
    public Activity mActivity;
    private final Context mContext;

    public InventoryList(Context context, ArrayList<String> grList, ArrayList<Object> childItem)
    {
        mGroupItem = grList;
        mChildItem = childItem;
        mContext = context;
    }

    public void setInflater(LayoutInflater inflater, Activity act)
    {
        mInflater = inflater;
        mActivity = act;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        return null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition)
    {
        return 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent)
    {
        mTempChild = (ArrayList<InventoryListItem>) mChildItem.get(groupPosition);
        TextView text;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.inventory_childrow, parent, false);
        }
        text = convertView.findViewById(R.id.agentlevel);
        var item = mTempChild.get(childPosition);
        text.setText(item.getPrettyDescription());
        ImageView image = convertView.findViewById(R.id.childImage);
        image.setImageDrawable(item.getIcon());
//        image.setImageURI(Uri.parse(mTempChild.get(childPosition).getImage()));
        // race condition
//        String uri = mTempChild.get(childPosition).getImage().replace("t_lim1kstripfaces", "t_lim1kstripfaces_32");
//        new Thread(() -> {
//            Bitmap bitmap;
//            bitmap = getImageBitmap(uri, mActivity.getApplicationContext().getCacheDir());
//            if (bitmap != null) {
//                mActivity.runOnUiThread(() -> image.setImageBitmap(bitmap));
//            }
//        }).start();
        convertView.setOnClickListener(v -> {
            Intent intent = new Intent(mContext, ActivityInventoryItem.class);
            intent.putExtra("item", item);
            mContext.startActivity(intent);
        });
        return convertView;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int getChildrenCount(int groupPosition)
    {
        return ((ArrayList<String>) mChildItem.get(groupPosition)).size();
    }

    @Override
    public Object getGroup(int groupPosition)
    {
        return null;
    }

    @Override
    public int getGroupCount()
    {
        return mGroupItem.size();
    }

    @Override
    public void onGroupCollapsed(int groupPosition)
    {
        super.onGroupCollapsed(groupPosition);
    }

    @Override
    public void onGroupExpanded(int groupPosition)
    {
        super.onGroupExpanded(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition)
    {
        return 0;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent)
    {
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.inventory_grouprow, parent, false);
        }
        ((CheckedTextView)convertView).setText(mGroupItem.get(groupPosition));
        ((CheckedTextView)convertView).setChecked(isExpanded);
        return convertView;
    }

    @Override
    public boolean hasStableIds()
    {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition)
    {
        return false;
    }
}
