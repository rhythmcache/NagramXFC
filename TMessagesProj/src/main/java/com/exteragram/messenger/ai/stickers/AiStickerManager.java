package com.exteragram.messenger.ai.stickers;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AiStickerManager {

    private static final Gson GSON = new Gson();
    private static final String PREF_NAME = "aichatconfig";
    private static final Map<Integer, List<AiSticker>> cache = new ConcurrentHashMap<>();

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, 0);
    }

    private static String getPrefKey(int account) {
        return account + "_ai_defined_stickers";
    }

    public static synchronized List<AiSticker> getDefinedStickers(int account) {
        List<AiSticker> cached = cache.get(account);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        String json = getPrefs().getString(getPrefKey(account), null);
        List<AiSticker> list = new ArrayList<>();
        if (!TextUtils.isEmpty(json)) {
            try {
                Type type = new TypeToken<ArrayList<AiSticker>>() {}.getType();
                ArrayList<AiSticker> parsed = GSON.fromJson(json, type);
                if (parsed != null) {
                    list.addAll(parsed);
                }
            } catch (Exception e) {
                FileLog.e("AiStickerManager load error", e);
            }
        }
        cache.put(account, list);
        return new ArrayList<>(list);
    }

    public static synchronized AiSticker getSticker(int account, long documentId) {
        List<AiSticker> list = cache.get(account);
        if (list == null) {
            list = getDefinedStickers(account);
        }
        for (AiSticker s : list) {
            if (s.documentId == documentId) {
                return s;
            }
        }
        return null;
    }

    public static synchronized boolean isStickerDefined(int account, long documentId) {
        return getSticker(account, documentId) != null;
    }

    public static void saveSticker(int account, TLRPC.Document document, String emoji, String description) {
        saveSticker(account, document, emoji, description, 0, 0, null);
    }

    public static void saveSticker(int account, TLRPC.Document document, String emoji, String description, long originDialogId, int originMsgId) {
        saveSticker(account, document, emoji, description, originDialogId, originMsgId, null);
    }

    public static void saveSticker(int account, TLRPC.Document document, String emoji, String description, long originDialogId, int originMsgId, Object parentObject) {
        if (document == null) return;
        String hex = AiSticker.serializeDocument(document);
        if (hex == null) return;

        boolean isAnimated = MessageObject.isAnimatedStickerDocument(document, true);
        boolean isVideo = MessageObject.isVideoSticker(document);
        boolean isGif = MessageObject.isGifDocument(document);

        if (TextUtils.isEmpty(emoji)) {
            emoji = extractEmoji(document);
        }

        synchronized (AiStickerManager.class) {
            List<AiSticker> list = getDefinedStickers(account);

            // Replace existing or add new
            int existingIndex = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).documentId == document.id) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0 && originDialogId == 0 && list.get(existingIndex).originDialogId != 0) {
                originDialogId = list.get(existingIndex).originDialogId;
                originMsgId = list.get(existingIndex).originMessageId;
            }

            AiSticker sticker = new AiSticker(document.id, emoji, description != null ? description.trim() : "", isAnimated, isVideo, isGif, hex, originDialogId, originMsgId);
            if (existingIndex >= 0) {
                list.set(existingIndex, sticker);
            } else {
                list.add(0, sticker);
            }

            cache.put(account, list);
            persist(account, list);
        }

        // Outside synchronized block: ensure GIF is saved to cloud Saved GIFs if not already saved
        if (isGif) {
            try {
                if (!MediaDataController.getInstance(account).hasRecentGif(document)) {
                    Object parent = parentObject != null ? parentObject : document;
                    MessagesController.getInstance(account).saveGif(parent, document);
                }
            } catch (Exception ignore) {}
        }
    }

    public static synchronized void removeSticker(int account, long documentId) {
        List<AiSticker> list = getDefinedStickers(account);
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).documentId == documentId) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            cache.put(account, list);
            persist(account, list);
        }
    }

    public static synchronized void clearAllStickers(int account) {
        cache.put(account, new ArrayList<>());
        getPrefs().edit().remove(getPrefKey(account)).apply();
    }

    private static void persist(int account, List<AiSticker> list) {
        try {
            String json = GSON.toJson(list);
            getPrefs().edit().putString(getPrefKey(account), json).apply();
        } catch (Exception e) {
            FileLog.e("AiStickerManager persist error", e);
        }
    }

    public static String extractEmoji(TLRPC.Document document) {
        if (document == null || document.attributes == null) return null;
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attr = document.attributes.get(a);
            if (attr instanceof TLRPC.TL_documentAttributeSticker || attr instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                if (!TextUtils.isEmpty(attr.alt)) {
                    return attr.alt;
                }
            }
        }
        return null;
    }

    public static void showDefineStickerDialog(Context context, int account, TLRPC.Document document, String emoji, Runnable onUpdated) {
        showDefineStickerDialog(context, account, document, emoji, 0, 0, null, onUpdated);
    }

    public static void showDefineStickerDialog(Context context, int account, TLRPC.Document document, String emoji, long originDialogId, int originMsgId, Runnable onUpdated) {
        showDefineStickerDialog(context, account, document, emoji, originDialogId, originMsgId, null, onUpdated);
    }

    public static void showDefineStickerDialog(Context context, int account, TLRPC.Document document, String emoji, long originDialogId, int originMsgId, Object parentObject, Runnable onUpdated) {
        if (context == null || document == null) return;

        final AiSticker existing = getSticker(account, document.id);
        final boolean isEdit = existing != null;
        final boolean isGif = MessageObject.isGifDocument(document);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(isEdit ? "AiEditSticker" : "AiDefineSticker", isEdit ? R.string.AiEditSticker : R.string.AiDefineSticker));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        // Top preview row: Sticker image + Emoji/Type
        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);

        BackupImageView imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setImage(ImageLocation.getForDocument(document), "80_80", null, null, document);
        previewRow.addView(imageView, LayoutHelper.createLinear(56, 56));

        LinearLayout textInfoLayout = new LinearLayout(context);
        textInfoLayout.setOrientation(LinearLayout.VERTICAL);
        textInfoLayout.setPadding(AndroidUtilities.dp(16), 0, 0, 0);

        String effectiveEmoji = !TextUtils.isEmpty(emoji) ? emoji : (existing != null && !TextUtils.isEmpty(existing.emoji) ? existing.emoji : extractEmoji(document));
        TextView emojiView = new TextView(context);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        emojiView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        emojiView.setText(!TextUtils.isEmpty(effectiveEmoji) ? effectiveEmoji : (isGif ? "GIF" : "Sticker"));
        textInfoLayout.addView(emojiView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView hintLabel = new TextView(context);
        hintLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hintLabel.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        hintLabel.setText(LocaleController.getString(!isEdit && isGif ? "AiDefineGifTitle" : "AiDefineStickerTitle", !isEdit && isGif ? R.string.AiDefineGifTitle : R.string.AiDefineStickerTitle));
        textInfoLayout.addView(hintLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        previewRow.addView(textInfoLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(previewRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Description input field
        EditText editTextView = new EditText(context);
        editTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editTextView.setHint(LocaleController.getString("AiDefineStickerHint", R.string.AiDefineStickerHint));
        editTextView.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        editTextView.setBackground(Theme.createEditTextDrawable(context, true));
        editTextView.setMaxLines(5);
        editTextView.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);

        if (existing != null && !TextUtils.isEmpty(existing.description)) {
            editTextView.setText(existing.description);
            editTextView.setSelection(existing.description.length());
        }

        contentLayout.addView(editTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 8));
        builder.setView(contentLayout);

        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialogInterface, which) -> {
            String desc = editTextView.getText().toString().trim();
            saveSticker(account, document, effectiveEmoji, desc, originDialogId, originMsgId, parentObject);
            BulletinFactory.global().createSimpleBulletin(
                    R.drawable.magic_stick,
                    LocaleController.getString(!isEdit && isGif ? "AiDefineGifSaved" : "AiDefineStickerSaved", !isEdit && isGif ? R.string.AiDefineGifSaved : R.string.AiDefineStickerSaved)
            ).show();
            if (onUpdated != null) {
                onUpdated.run();
            }
        });

        if (isEdit) {
            builder.setNeutralButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, which) -> {
                removeSticker(account, document.id);
                BulletinFactory.global().createSimpleBulletin(
                        R.drawable.msg_delete,
                        LocaleController.getString("AiDefineStickerDeleted", R.string.AiDefineStickerDeleted)
                ).show();
                if (onUpdated != null) {
                    onUpdated.run();
                }
            });
        }

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setOnPreDismissListener(dialogInterface -> AndroidUtilities.hideKeyboard(editTextView));

        AlertDialog dialog = builder.create();
        dialog.show();

        if (isEdit) {
            TextView deleteButton = (TextView) dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (deleteButton != null) {
                deleteButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        }

        editTextView.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editTextView), 100);
    }
}
