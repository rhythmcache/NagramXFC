package com.exteragram.messenger.ai.ui.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.exteragram.messenger.ai.AiConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

public class AiAutoReplyPromptDialog {

    public static void show(BaseFragment fragment, int account, long dialogId, Runnable onUpdated) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        Context context = fragment.getParentActivity();

        String chatTitle;
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            chatTitle = chat != null && chat.title != null ? chat.title : LocaleController.getString("Group", R.string.Group);
        } else {
            TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            chatTitle = user != null ? UserObject.getUserName(user) : LocaleController.getString("Chat", R.string.Chat);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(chatTitle + " • " + LocaleController.getString("AIChatAutoReplyChatPrompt", R.string.AIChatAutoReplyChatPrompt));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        EditText editText = new EditText(context);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHint(LocaleController.getString("AIChatAutoReplyChatPromptHint", R.string.AIChatAutoReplyChatPromptHint));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        editText.setBackground(Theme.createEditTextDrawable(context, true));
        editText.setMinLines(3);
        editText.setMaxLines(8);
        editText.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        String existing = AiConfig.getAutoReplyCustomPrompt(account, dialogId);
        if (!TextUtils.isEmpty(existing)) {
            editText.setText(existing);
            editText.setSelection(existing.length());
        }

        layout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(layout);

        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String prompt = editText.getText().toString().trim();
            AiConfig.setAutoReplyCustomPrompt(account, dialogId, prompt);
            if (BulletinFactory.canShowBulletin(fragment)) {
                BulletinFactory.of(fragment).createSimpleBulletin(
                        R.drawable.magic_stick,
                        LocaleController.getString(TextUtils.isEmpty(prompt) ? "AIChatAutoReplyChatPromptReset" : "AIChatAutoReplyChatPromptSaved",
                                TextUtils.isEmpty(prompt) ? R.string.AIChatAutoReplyChatPromptReset : R.string.AIChatAutoReplyChatPromptSaved)
                ).show();
            }
            if (onUpdated != null) {
                onUpdated.run();
            }
        });

        if (!TextUtils.isEmpty(existing)) {
            builder.setNeutralButton(LocaleController.getString("AIChatAutoReplyResetToGlobal", R.string.AIChatAutoReplyResetToGlobal), (dialog, which) -> {
                AiConfig.setAutoReplyCustomPrompt(account, dialogId, null);
                if (BulletinFactory.canShowBulletin(fragment)) {
                    BulletinFactory.of(fragment).createSimpleBulletin(
                            R.drawable.magic_stick,
                            LocaleController.getString("AIChatAutoReplyChatPromptReset", R.string.AIChatAutoReplyChatPromptReset)
                    ).show();
                }
                if (onUpdated != null) {
                    onUpdated.run();
                }
            });
        }

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setOnPreDismissListener(dialog -> AndroidUtilities.hideKeyboard(editText));

        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);

        editText.post(() -> AndroidUtilities.showKeyboard(editText));
    }
}
