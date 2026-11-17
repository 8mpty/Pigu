package moe.shizuku.manager.management;

import android.content.pm.PackageInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rikka.recyclerview.BaseRecyclerViewAdapter;
import rikka.recyclerview.ClassCreatorPool;

public class AppsAdapter extends BaseRecyclerViewAdapter<ClassCreatorPool> {

    public static final class HeaderMarker {}

    private Set<Integer> grantedUids = new HashSet<>();

    public AppsAdapter() {
        super();

        getCreatorPool().putRule(HeaderMarker.class, ToggleAllViewHolder.CREATOR);
        getCreatorPool().putRule(PackageInfo.class, AppViewHolder.CREATOR);
        getCreatorPool().putRule(Object.class, EmptyViewHolder.CREATOR);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItemAt(position).hashCode();
    }

    @Override
    public ClassCreatorPool onCreateCreatorPool() {
        return new ClassCreatorPool();
    }

    public void updateData(List<PackageInfo> data, Set<Integer> grantedUids) {
        this.grantedUids = new HashSet<>(grantedUids);
        getItems().clear();
        if (data.isEmpty()) {
            getItems().add(new Object());
        } else {
            getItems().add(new HeaderMarker());
            getItems().addAll(data);
        }
        notifyDataSetChanged();
    }

    public boolean isGranted(int uid) {
        return grantedUids.contains(uid);
    }

    public void updateGranted(int uid, boolean granted) {
        if (granted) {
            grantedUids.add(uid);
        } else {
            grantedUids.remove(uid);
        }
    }

    public boolean allGranted() {
        boolean found = false;
        for (Object item : getItems()) {
            if (item instanceof PackageInfo) {
                found = true;
                PackageInfo pi = (PackageInfo) item;
                if (pi.applicationInfo == null || !isGranted(pi.applicationInfo.uid)) {
                    return false;
                }
            }
        }
        return found;
    }

    public int appCount() {
        int count = 0;
        for (Object item : getItems()) {
            if (item instanceof PackageInfo) count++;
        }
        return count;
    }

    public List<PackageInfo> apps() {
        List<PackageInfo> result = new java.util.ArrayList<>();
        for (Object item : getItems()) {
            if (item instanceof PackageInfo) result.add((PackageInfo) item);
        }
        return result;
    }
}
