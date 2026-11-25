package org.telegram.ui.legacy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.Nullable;

import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.DialogsActivityTopBubblesFadeView;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;

/**
 * Helpers for the pre-redesign (legacy) chat input bar and dialogs header metrics
 * when {@link SharedConfig#legacyChatUi} is enabled.
 */
public final class LegacyUiHelper {

    private static final Paint DOCKED_BG_PAINT = new Paint();

    private LegacyUiHelper() {
    }

    public static boolean isLegacyUi() {
        return SharedConfig.legacyChatUi;
    }

    public static int getInputBubbleBottomDp(int defaultDp) {
        return SharedConfig.legacyChatUi ? 0 : defaultDp;
    }

    public static int getInputIslandSideMarginDp(int defaultDp) {
        return SharedConfig.legacyChatUi ? 0 : defaultDp;
    }

    public static int getInputIslandListExtraBottomDp() {
        return SharedConfig.legacyChatUi ? 0 : 9 + 7;
    }

    public static int getTopPanelPaddingDp() {
        return SharedConfig.legacyChatUi ? 0 : 7;
    }

    public static int getTopPanelOverlapDp() {
        return SharedConfig.legacyChatUi ? 0 : 5;
    }

    public static int getChatListTopOffset(@Nullable ActionBar actionBar, @Nullable Object parentChatActivity, boolean inPreviewMode) {
        if (!SharedConfig.legacyChatUi || actionBar == null || parentChatActivity != null || inPreviewMode) {
            return 0;
        }
        return actionBar.getVisibility() == View.VISIBLE ? actionBar.getMeasuredHeight() : 0;
    }

    public static void configureChatActionBar(@Nullable ActionBar actionBar, @Nullable Theme.ResourcesProvider resourcesProvider) {
        if (SharedConfig.legacyChatUi && actionBar != null) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider));
        }
    }

    public static void drawFlatTopPanelBackground(
            Canvas canvas,
            int left,
            int top,
            int right,
            int bottom,
            float alpha,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        DOCKED_BG_PAINT.setColor(Theme.getColor(Theme.key_chat_topPanelBackground, resourcesProvider));
        DOCKED_BG_PAINT.setAlpha((int) (255 * alpha));
        canvas.drawRect(left, top, right, bottom, DOCKED_BG_PAINT);
        DOCKED_BG_PAINT.setAlpha(255);
        final int wasAlpha = Theme.dividerPaint.getAlpha();
        Theme.dividerPaint.setAlpha((int) (wasAlpha * alpha));
        canvas.drawLine(left, bottom - 1, right, bottom - 1, Theme.dividerPaint);
        Theme.dividerPaint.setAlpha(wasAlpha);
    }

    /**
     * Draws a solid docked panel background for the chat input bar and in-app keyboard
     * instead of the floating Liquid Glass island.
     */
    public static void drawDockedInputBackground(
            Canvas canvas,
            int width,
            int height,
            int inputTop,
            int inputHeight,
            boolean drawInAppKeyboard,
            float imeBottomInset,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        DOCKED_BG_PAINT.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider));
        canvas.drawRect(0, inputTop, width, height, DOCKED_BG_PAINT);
        canvas.drawLine(0, inputTop, width, inputTop, Theme.dividerPaint);
    }

    /**
     * Applies classic solid header metrics to {@code DialogsActivity} top views.
     */
    public static void configureDialogsHeader(
            @Nullable DialogsActivityTopBubblesFadeView topBubblesFadeView,
            @Nullable DialogsActivityTopPanelLayout topPanelLayout,
            @Nullable View searchTabsAndFiltersLayout,
            @Nullable ActionBar actionBar,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        if (!SharedConfig.legacyChatUi) {
            return;
        }
        if (topBubblesFadeView != null) {
            topBubblesFadeView.setVisibility(View.GONE);
        }
        if (topPanelLayout != null) {
            topPanelLayout.setBlurredBackground(null);
            topPanelLayout.setPadding(0, 0, 0, 0);
            topPanelLayout.setDefaultRadiusDp(0);
            topPanelLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        }
        if (searchTabsAndFiltersLayout != null) {
            searchTabsAndFiltersLayout.setPadding(0, 0, 0, 0);
            searchTabsAndFiltersLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        }
        if (actionBar != null) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider));
        }
    }
}
