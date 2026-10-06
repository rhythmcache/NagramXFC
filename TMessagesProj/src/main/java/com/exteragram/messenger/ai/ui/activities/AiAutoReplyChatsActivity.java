package com.exteragram.messenger.ai.ui.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ai.AiConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

public class AiAutoReplyChatsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_DISABLE_ALL = 1;
    private static final int VIEW_TYPE_USER = 100;
    private static final int VIEW_TYPE_EMPTY = 101;

    private final ArrayList<Long> dialogs = new ArrayList<>();
    private ActionBarMenuItem menuItemDisableAll;

    @Override
    public boolean onFragmentCreate() {
        reloadDialogs();
        return super.onFragmentCreate();
    }

    private void reloadDialogs() {
        dialogs.clear();
        dialogs.addAll(AiConfig.getAutoReplyEnabledDialogs(currentAccount));
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);

        actionBar.setTitle(LocaleController.getString("AIChatAutoReplyChats", R.string.AIChatAutoReplyChats));
        updateSubtitle();

        ActionBarMenu menu = actionBar.createMenu();
        menuItemDisableAll = menu.addItem(MENU_DISABLE_ALL, LocaleController.getString("AIChatAutoReplyDisableAll", R.string.AIChatAutoReplyDisableAll));
        if (menuItemDisableAll != null) {
            menuItemDisableAll.setVisibility(dialogs.isEmpty() ? View.GONE : View.VISIBLE);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_DISABLE_ALL) {
                    if (getParentActivity() == null || dialogs.isEmpty()) return;
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AIChatAutoReplyDisableAll", R.string.AIChatAutoReplyDisableAll));
                    builder.setMessage(LocaleController.getString("AIChatAutoReplyDisableAllConfirm", R.string.AIChatAutoReplyDisableAllConfirm));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                        AiConfig.disableAllAutoReply(currentAccount);
                        dialogs.clear();
                        updateRows();
                        updateSubtitle();
                        if (menuItemDisableAll != null) {
                            menuItemDisableAll.setVisibility(View.GONE);
                        }
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                        BulletinFactory.of(AiAutoReplyChatsActivity.this).createSimpleBulletin(
                                R.drawable.magic_stick,
                                LocaleController.getString("AutoReplyDisabled", R.string.AutoReplyDisabled)
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
            actionBar.setSubtitle(dialogs.isEmpty() ? null : LocaleController.formatPluralString("Chats", dialogs.size()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadDialogs();
        updateRows();
        updateSubtitle();
        if (menuItemDisableAll != null) {
            menuItemDisableAll.setVisibility(dialogs.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void updateRows() {
        rowCount = dialogs.isEmpty() ? 1 : dialogs.size();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (!dialogs.isEmpty() && position >= 0 && position < dialogs.size()) {
            long did = dialogs.get(position);
            Bundle args = new Bundle();
            if (did > 0) {
                args.putLong("user_id", did);
            } else {
                args.putLong("chat_id", -did);
            }
            presentFragment(new ChatActivity(args));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new BaseListAdapter() {
            @Override
            public int getItemCount() {
                return rowCount;
            }

            @Override
            public int getItemViewType(int position) {
                return dialogs.isEmpty() ? VIEW_TYPE_EMPTY : VIEW_TYPE_USER;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == VIEW_TYPE_USER;
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
                    UserCell cell = new UserCell(context, 4, 0, false);
                    view = cell;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == VIEW_TYPE_EMPTY) {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(LocaleController.getString("AIChatAutoReplyNoChats", R.string.AIChatAutoReplyNoChats));
                } else if (holder.getItemViewType() == VIEW_TYPE_USER) {
                    UserCell cell = (UserCell) holder.itemView;
                    if (position >= 0 && position < dialogs.size()) {
                        long did = dialogs.get(position);
                        boolean needDivider = position != dialogs.size() - 1;
                        if (did > 0) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                            String name = user != null ? UserObject.getUserName(user) : "User " + did;
                            cell.setData(user, name, LocaleController.getString("AIChatAutoReplyActive", R.string.AIChatAutoReplyActive), 0, needDivider);
                        } else {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                            String title = chat != null && chat.title != null ? chat.title : "Group " + (-did);
                            cell.setData(chat, title, LocaleController.getString("AIChatAutoReplyActive", R.string.AIChatAutoReplyActive), 0, needDivider);
                        }
                        cell.setCloseIcon(v -> {
                            AiConfig.setAutoReplyEnabled(currentAccount, did, false);
                            int idx = dialogs.indexOf(did);
                            if (idx >= 0) {
                                dialogs.remove(idx);
                                updateRows();
                                updateSubtitle();
                                if (dialogs.isEmpty()) {
                                    if (menuItemDisableAll != null) {
                                        menuItemDisableAll.setVisibility(View.GONE);
                                    }
                                    notifyDataSetChanged();
                                } else {
                                    notifyItemRemoved(idx);
                                }
                            }
                            BulletinFactory.of(AiAutoReplyChatsActivity.this).createSimpleBulletin(
                                    R.drawable.magic_stick,
                                    LocaleController.getString("AutoReplyDisabled", R.string.AutoReplyDisabled)
                            ).show();
                        });
                    }
                }
            }
        };
    }
}
