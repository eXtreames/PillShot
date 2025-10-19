package ru.extreames.pillshot.xposed;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.extreames.pillshot.utiils.Utils;

public class XposedInit implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) {
        try {
            Class<?> ScreenshotController = XposedHelpers.findClass("com.android.systemui.screenshot.ScreenshotController", lpParam.classLoader);
            XposedBridge.hookAllMethods(
                    ScreenshotController,
                    "handleScreenshot",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object self = param.thisObject;
                                Object screenshotData = param.args[0];

                                if (self == null || screenshotData == null)
                                    return;

                                Context context = (Context) XposedHelpers.getObjectField(self, "context");
                                Bitmap bitmap = (Bitmap) XposedHelpers.getObjectField(screenshotData, "bitmap");
                                String packageName = (String) Utils.tryCall(screenshotData, "getPackageNameString");

                                if (context == null || bitmap == null || packageName == null)
                                    return;

                                String currentAppName = extractAppName(context, packageName);
                                Bitmap modifiedBitmap = drawApp(context, currentAppName, bitmap);

                                if (modifiedBitmap == null)
                                    return;

                                XposedHelpers.setObjectField(screenshotData, "bitmap", modifiedBitmap);
                            }
                            catch (Exception e) {
                                Utils.log(Utils.DEBUG_LEVEL.ERROR, e.toString());
                            }
                        }
                    });
            Utils.log(Utils.DEBUG_LEVEL.INFO, "Intercepted controller!");
        }
        catch (Exception e) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, e.toString());
        }
    }

    private String extractAppName(Context context, String packageName) {
        try {
            PackageManager packageManager= context.getPackageManager();
            return (String) packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
        } catch (Exception ignored) {
            return packageName;
        }
    }

    private Bitmap drawApp(Context context, String appName, Bitmap bitmap) {
        if (bitmap == null || appName == null || appName.isEmpty())
            return bitmap;

        Bitmap mutable = bitmap.isMutable() ? bitmap : bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);

        final float density = context.getResources().getDisplayMetrics().density;
        final int screenWidth = mutable.getWidth();
        final boolean isPortrait = bitmap.getHeight() > bitmap.getWidth();

        final int pillTopY = (int) ((isPortrait ? 12 : 2) * density);
        final int textSize = (int) (16 * density);
        final int minWidth = (int) (80 * density);
        final int cornerRadius = (int) (18 * density);
        final int horizontalPad = (int) (12 * density);
        final int verticalPad = (int) ((isPortrait ? 8 : 6) * density);

        Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillPaint.setColor(Color.argb(255, 28, 28, 30));

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);

        Rect textBounds = new Rect();
        textPaint.getTextBounds(appName, 0, appName.length(), textBounds);

        int pillWidth = Math.max(textBounds.width() + horizontalPad * 2, minWidth);
        int pillHeight = textBounds.height() + verticalPad * 2;

        int pillLeft = screenWidth / 2 - pillWidth / 2;
        int pillRight = pillLeft + pillWidth;
        int pillBottom = pillTopY + pillHeight;

        canvas.drawRoundRect(
                pillLeft, pillTopY, pillRight, pillBottom,
                cornerRadius, cornerRadius, pillPaint
        );

        float textY = pillTopY + pillHeight / 2f + textBounds.height() / 2f - textBounds.bottom;
        canvas.drawText(appName, screenWidth / 2f, textY, textPaint);

        return mutable;
    }
}
