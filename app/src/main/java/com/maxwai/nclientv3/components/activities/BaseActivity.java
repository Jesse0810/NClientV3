package com.maxwai.nclientv3.components.activities;

import android.content.res.Configuration;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.widgets.CustomGridLayoutManager;
import com.maxwai.nclientv3.settings.Global;

public abstract class BaseActivity extends GeneralActivity {
    protected RecyclerView recycler;
    protected SwipeRefreshLayout refresher;
    protected ViewGroup masterLayout;
    private MenuItem listColumnsItem;
    private AlertDialog listColumnsDialog;

    protected abstract int getPortraitColumnCount();

    protected abstract int getLandscapeColumnCount();

    /** Only comic lists opt in; detail pages and comments keep their own layouts. */
    protected int getListColumnPreference(boolean landscape) {
        return 0;
    }

    protected void addListColumnMenu(Menu menu) {
        if (getListColumnPreference(isLandscape()) == 0) return;
        getMenuInflater().inflate(R.menu.list_columns, menu);
        listColumnsItem = menu.findItem(R.id.list_columns);
        updateListColumnsTitle();
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void updateListColumnsTitle() {
        if (listColumnsItem == null) return;
        int count = isLandscape() ? getLandscapeColumnCount() : getPortraitColumnCount();
        listColumnsItem.setTitle(getString(R.string.list_columns_action, Global.clampListColumnCount(count)));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.list_columns && getListColumnPreference(isLandscape()) != 0) {
            showListColumnsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showListColumnsDialog() {
        if (listColumnsDialog != null && listColumnsDialog.isShowing()) return;
        final boolean landscape = isLandscape();
        int current = Global.clampListColumnCount(landscape ? getLandscapeColumnCount() : getPortraitColumnCount());
        String[] choices = new String[Global.MAX_LIST_COLUMNS];
        for (int i = 0; i < choices.length; i++) choices[i] = getString(R.string.list_columns_count, i + 1);
        int[] selected = {current};
        listColumnsDialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.list_columns_title)
            .setSingleChoiceItems(choices, current - 1, (dialog, which) -> selected[0] = which + 1)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                if (landscape != isLandscape()) return;
                Global.updateListColumnCount(this, getListColumnPreference(landscape), selected[0]);
                changeLayout(landscape);
            })
            .setNegativeButton(R.string.cancel, null)
            .create();
        listColumnsDialog.setOnDismissListener(dialog -> listColumnsDialog = null);
        listColumnsDialog.show();
    }

    private void dismissListColumnsDialog() {
        if (listColumnsDialog != null) listColumnsDialog.dismiss();
        listColumnsDialog = null;
    }

    public SwipeRefreshLayout getRefresher() {
        return refresher;
    }

    public RecyclerView getRecycler() {
        return recycler;
    }

    public ViewGroup getMasterLayout() {
        return masterLayout;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        dismissListColumnsDialog();
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            changeLayout(true);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            changeLayout(false);
        }
    }

    protected void changeLayout(boolean landscape) {
        if (getListColumnPreference(landscape) != 0) {
            updateListLayout(landscape);
            return;
        }
        CustomGridLayoutManager manager = (CustomGridLayoutManager) recycler.getLayoutManager();
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        int count = landscape ? getLandscapeColumnCount() : getPortraitColumnCount();
        int position = 0;

        if (manager != null)
            position = manager.findFirstCompletelyVisibleItemPosition();
        CustomGridLayoutManager gridLayoutManager = new CustomGridLayoutManager(this, count);
        recycler.setLayoutManager(gridLayoutManager);
        recycler.setAdapter(adapter);
        recycler.scrollToPosition(position);
    }

    private void updateListLayout(boolean landscape) {
        if (recycler == null) return;
        int count = Global.clampListColumnCount(landscape ? getLandscapeColumnCount() : getPortraitColumnCount());
        CustomGridLayoutManager manager = (CustomGridLayoutManager) recycler.getLayoutManager();
        if (manager == null) {
            recycler.setLayoutManager(new CustomGridLayoutManager(this, count));
        } else if (manager.getSpanCount() != count) {
            int position = manager.findFirstVisibleItemPosition();
            View anchor = position == RecyclerView.NO_POSITION ? null : manager.findViewByPosition(position);
            int offset = anchor == null ? 0 : manager.getDecoratedTop(anchor) - recycler.getPaddingTop();
            manager.setSpanCount(count);
            RecyclerView.Adapter<?> adapter = recycler.getAdapter();
            if (position != RecyclerView.NO_POSITION && adapter != null && adapter.getItemCount() > 0)
                manager.scrollToPositionWithOffset(Math.min(position, adapter.getItemCount() - 1), offset);
        }
        updateListColumnsTitle();
    }

    @Override
    protected void onDestroy() {
        dismissListColumnsDialog();
        super.onDestroy();
    }
}
