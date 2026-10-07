package com.exteragram.messenger.ai.ui.activities;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ai.stickers.AiSticker;
import com.exteragram.messenger.ai.stickers.AiStickerManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

public class AiStickersActivity extends BaseNekoSettingsActivity {

    private static final int MENU_CLEAR_ALL = 1;
    private static final int VIEW_TYPE_STICKER = 100;
    private static final int VIEW_TYPE_EMPTY = 101;

    private final ArrayList<AiSticker> stickers = new ArrayList<>();
    private ActionBarMenuItem menuItemClearAll;

    @Override
    public boolean onFragmentCreate() {
        reloadStickers();
        return super.onFragmentCreate();
    }

    private void reloadStickers() {
        stickers.clear();
        stickers.addAll(AiStickerManager.getDefinedStickers(currentAccount));
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("AIChatAutoReplyStickers", R.string.AIChatAutoReplyStickers);
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);

        actionBar.setTitle(LocaleController.getString("AIChatAutoReplyStickers", R.string.AIChatAutoReplyStickers));
        updateSubtitle();

        ActionBarMenu menu = actionBar.createMenu();
        menuItemClearAll = menu.addItem(MENU_CLEAR_ALL, LocaleController.getString("AiStickersClearAll", R.string.AiStickersClearAll));
        if (menuItemClearAll != null) {
            menuItemClearAll.setVisibility(stickers.isEmpty() ? View.GONE : View.VISIBLE);
        }

        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CLEAR_ALL) {
                    if (getParentActivity() == null || stickers.isEmpty()) return;
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AiStickersClearAll", R.string.AiStickersClearAll));
                    builder.setMessage(LocaleController.getString("AiStickersClearAllConfirm", R.string.AiStickersClearAllConfirm));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                        AiStickerManager.clearAllStickers(currentAccount);
                        stickers.clear();
                        updateRows();
                        updateSubtitle();
                        if (menuItemClearAll != null) {
                            menuItemClearAll.setVisibility(View.GONE);
                        }
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                        BulletinFactory.of(AiStickersActivity.this).createSimpleBulletin(
                                R.drawable.msg_delete,
                                LocaleController.getString("AiDefineStickerDeleted", R.string.AiDefineStickerDeleted)
                        ).show();
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            }
        });

        return view;
    }

    private void updateSubtitle() {
        if (actionBar != null) {
            actionBar.setSubtitle(stickers.isEmpty() ? null : LocaleController.formatPluralString("Stickers", stickers.size()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadStickers();
        updateRows();
        updateSubtitle();
        if (menuItemClearAll != null) {
            menuItemClearAll.setVisibility(stickers.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void updateRows() {
        rowCount = stickers.isEmpty() ? 1 : stickers.size();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (!stickers.isEmpty() && position >= 0 && position < stickers.size()) {
            AiSticker sticker = stickers.get(position);
            TLRPC.Document doc = sticker.getDocument();
            if (doc != null && getParentActivity() != null) {
                AiStickerManager.showDefineStickerDialog(getParentActivity(), currentAccount, doc, sticker.emoji, sticker.originDialogId, sticker.originMessageId, () -> {
                    reloadStickers();
                    updateRows();
                    updateSubtitle();
                    if (menuItemClearAll != null) {
                        menuItemClearAll.setVisibility(stickers.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new BaseListAdapter(context) {
            @Override
            public int getItemCount() {
                return rowCount;
            }

            @Override
            public int getItemViewType(int position) {
                return stickers.isEmpty() ? VIEW_TYPE_EMPTY : VIEW_TYPE_STICKER;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == VIEW_TYPE_STICKER;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == VIEW_TYPE_EMPTY) {
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
                    cell.setFixedSize(0);
                    view = cell;
                } else {
                    view = new StickerCell(context);
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == VIEW_TYPE_EMPTY) {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(LocaleController.getString("AiStickersEmpty", R.string.AiStickersEmpty));
                } else if (holder.getItemViewType() == VIEW_TYPE_STICKER) {
                    StickerCell cell = (StickerCell) holder.itemView;
                    if (position >= 0 && position < stickers.size()) {
                        AiSticker item = stickers.get(position);
                        boolean needDivider = position != stickers.size() - 1;
                        cell.bind(item, needDivider, v -> {
                            AiStickerManager.removeSticker(currentAccount, item.documentId);
                            int idx = stickers.indexOf(item);
                            if (idx >= 0) {
                                stickers.remove(idx);
                                updateRows();
                                updateSubtitle();
                                if (stickers.isEmpty()) {
                                    if (menuItemClearAll != null) {
                                        menuItemClearAll.setVisibility(View.GONE);
                                    }
                                    notifyDataSetChanged();
                                } else {
                                    notifyItemRemoved(idx);
                                }
                            }
                            BulletinFactory.of(AiStickersActivity.this).createSimpleBulletin(
                                    R.drawable.msg_delete,
                                    LocaleController.getString("AiDefineStickerDeleted", R.string.AiDefineStickerDeleted)
                            ).show();
                        });
                    }
                }
            }
        };
    }

    private static class StickerCell extends FrameLayout {

        private final BackupImageView imageView;
        private final TextView titleView;
        private final TextView descView;
        private final ImageView deleteView;
        private final View divider;

        public StickerCell(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            imageView.setAspectFit(true);
            addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            descView = new TextView(context);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            descView.setMaxLines(2);
            descView.setEllipsize(TextUtils.TruncateAt.END);
            textLayout.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 76, 0, 56, 0));

            deleteView = new ImageView(context);
            deleteView.setImageResource(R.drawable.msg_delete);
            deleteView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            deleteView.setScaleType(ImageView.ScaleType.CENTER);
            deleteView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
            addView(deleteView, LayoutHelper.createFrame(48, 48, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            divider = new View(context);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM, 76, 0, 0, 0));

            setMinimumHeight(AndroidUtilities.dp(64));
        }

        public void bind(AiSticker item, boolean needDivider, OnClickListener onDelete) {
            TLRPC.Document doc = item.getDocument();
            if (doc != null) {
                imageView.setImage(ImageLocation.getForDocument(doc), "80_80", null, null, doc);
            } else {
                imageView.setImageDrawable(null);
            }

            String emoji = !TextUtils.isEmpty(item.emoji) ? item.emoji : "";
            titleView.setText(emoji + (item.isGif ? " [GIF]" : " [Sticker]"));
            descView.setText(!TextUtils.isEmpty(item.description) ? item.description : "No description");

            deleteView.setOnClickListener(onDelete);
            divider.setVisibility(needDivider ? View.VISIBLE : View.GONE);
        }
    }
}
