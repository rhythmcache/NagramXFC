package org.telegram.ui.legacy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
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

    public static boolean isTranslucentChatHeader() {
        return SharedConfig.legacyChatUi && SharedConfig.legacyChatTranslucentHeader;
    }

    public static int getInputBubbleBottomDp(int defaultDp) {
        return SharedConfig.legacyChatUi ? 4 : defaultDp;
    }

    public static int getInputIslandSideMarginDp(int defaultDp) {
        return SharedConfig.legacyChatUi ? 4 : defaultDp;
    }

    public static int getInputIslandListExtraBottomDp() {
        return SharedConfig.legacyChatUi ? 4 + 4 : 9 + 7;
    }

    public static int getSettingsListTopPaddingPx(int defaultPaddingPx) {
        return SharedConfig.legacyChatUi ? ActionBar.getCurrentActionBarHeight() : defaultPaddingPx;
    }

    public static int getFilterTabsHeightDp() {
        return 44;
    }

    public static int getTopPanelPaddingDp() {
        return SharedConfig.legacyChatUi ? 0 : 7;
    }

    public static int getTopPanelOverlapDp() {
        return SharedConfig.legacyChatUi ? 0 : 5;
    }

    public static int getChatListTopOffset(@Nullable ActionBar actionBar, @Nullable Object parentChatActivity, boolean inPreviewMode, boolean translucentHeader) {
        if (!isLegacyUi() || translucentHeader || actionBar == null || parentChatActivity != null || inPreviewMode) {
            return 0;
        }
        return actionBar.getVisibility() == View.VISIBLE ? actionBar.getMeasuredHeight() : 0;
    }

    public static void configureChatActionBar(@Nullable ActionBar actionBar, @Nullable Theme.ResourcesProvider resourcesProvider, boolean translucentHeader) {
        if (isLegacyUi() && actionBar != null) {
            int color = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            actionBar.setBackgroundColor(translucentHeader ? Theme.multAlpha(color, 0.85f) : color);
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
        DOCKED_BG_PAINT.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_topPanelBackground, resourcesProvider), alpha));
        canvas.drawRect(left, top, right, bottom, DOCKED_BG_PAINT);
        final int wasAlpha = Theme.dividerPaint.getAlpha();
        Theme.dividerPaint.setAlpha((int) (wasAlpha * alpha));
        canvas.drawLine(left, top, right, top, Theme.dividerPaint);
        canvas.drawLine(left, bottom - 1, right, bottom - 1, Theme.dividerPaint);
        Theme.dividerPaint.setAlpha(wasAlpha);
    }

    /**
     * Draws a solid docked panel background for the chat input bar and nav-bar area
     * instead of the floating Liquid Glass island.
     */
    public static void drawDockedInputBackground(
            Canvas canvas,
            int width,
            int height,
            int inputTop,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        final int top = inputTop - dp(4);
        DOCKED_BG_PAINT.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider));
        canvas.drawRect(0, top, width, height, DOCKED_BG_PAINT);
        canvas.drawLine(0, top, width, top, Theme.dividerPaint);
    }

    public static Drawable createFlatFilterTabsBackground(@Nullable Theme.ResourcesProvider resourcesProvider) {
        return new Drawable() {
            private int alpha = 255;

            @Override
            public void draw(@NonNull Canvas canvas) {
                final Rect b = getBounds();
                DOCKED_BG_PAINT.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), alpha / 255f));
                canvas.drawRect(b, DOCKED_BG_PAINT);
                final int wasAlpha = Theme.dividerPaint.getAlpha();
                Theme.dividerPaint.setAlpha((int) (wasAlpha * (alpha / 255f)));
                canvas.drawLine(b.left, b.bottom - 1, b.right, b.bottom - 1, Theme.dividerPaint);
                Theme.dividerPaint.setAlpha(wasAlpha);
            }

            @Override
            public void setAlpha(int alpha) {
                this.alpha = alpha;
                invalidateSelf();
            }

            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
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
