package io.ionic.keyboard;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

public class CDVIonicKeyboard extends CordovaPlugin {

    // Variáveis mantidas para o fallback legado (API < 30)
    private OnGlobalLayoutListener list;
    private View rootView;
    private boolean wasKeyboardVisible = false;
    private int previousHeightDp = 0;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if ("hide".equals(action)) {
            cordova.getActivity().runOnUiThread(() -> {
                View decorView = cordova.getActivity().getWindow().getDecorView();

                // Android 11+ (API 30+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    decorView.getWindowInsetsController().hide(WindowInsets.Type.ime());
                    callbackContext.success();
                } else {
                    // Fallback Android 10 ou inferior
                    InputMethodManager inputManager = (InputMethodManager) cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    View v = cordova.getActivity().getCurrentFocus();
                    if (v == null) {
                        callbackContext.error("No current focus");
                    } else {
                        inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                        callbackContext.success();
                    }
                }
            });
            return true;
        }

        if ("show".equals(action)) {
            cordova.getActivity().runOnUiThread(() -> {
                View decorView = cordova.getActivity().getWindow().getDecorView();

                // Android 11+ (API 30+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    decorView.getWindowInsetsController().show(WindowInsets.Type.ime());
                } else {
                    // Fallback Android 10 ou inferior
                    InputMethodManager imm = (InputMethodManager) cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(decorView, 0);
                }
                callbackContext.success();
            });
            return true;
        }

        if ("init".equals(action)) {
            cordova.getActivity().runOnUiThread(() -> {
                View decorView = cordova.getActivity().getWindow().getDecorView();
                final float density = decorView.getContext().getResources().getDisplayMetrics().density;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    decorView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                        boolean isKeyboardVisible = windowInsets.isVisible(WindowInsets.Type.ime());
                        int keyboardHeightPixels = windowInsets.getInsets(WindowInsets.Type.ime()).bottom;
                        int keyboardHeightDp = (int) (keyboardHeightPixels / density);

                        PluginResult result = null;

                        if (isKeyboardVisible && keyboardHeightDp > 0) {
                            if (!wasKeyboardVisible || keyboardHeightDp != previousHeightDp) {
                                wasKeyboardVisible = true;
                                previousHeightDp = keyboardHeightDp;
                                result = new PluginResult(PluginResult.Status.OK, "S" + keyboardHeightDp);
                            }
                        } else if (!isKeyboardVisible && wasKeyboardVisible) {
                            wasKeyboardVisible = false;
                            previousHeightDp = 0;
                            result = new PluginResult(PluginResult.Status.OK, "H");
                        }

                        if (result != null) {
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }

                        return view.onApplyWindowInsets(windowInsets);
                    });
                } else {
                    FrameLayout content = (FrameLayout) cordova.getActivity().findViewById(android.R.id.content);
                    rootView = content.getRootView();

                    list = new OnGlobalLayoutListener() {
                        int previousHeightDiff = 0;

                        @Override
                        public void onGlobalLayout() {
                            Rect r = new Rect();
                            rootView.getWindowVisibleDisplayFrame(r);

                            int rootViewHeight = rootView.getRootView().getHeight();
                            int resultBottom = r.bottom;
                            int screenHeight;

                            if (Build.VERSION.SDK_INT >= 23) {
                                WindowInsets windowInsets = rootView.getRootWindowInsets();
                                int stableInsetBottom = windowInsets.getStableInsetBottom();
                                screenHeight = rootViewHeight;
                                resultBottom = resultBottom + stableInsetBottom;
                            } else {
                                // calculate screen height differently for android versions <23: Lollipop 5.x, Marshmallow 6.x
                                //http://stackoverflow.com/a/29257533/3642890 beware of nexus 5
                                Display display = cordova.getActivity().getWindowManager().getDefaultDisplay();
                                Point size = new Point();
                                display.getSize(size);
                                screenHeight = size.y;
                            }

                            int heightDiff = screenHeight - resultBottom;
                            int pixelHeightDiff = (int) (heightDiff / density);
                            PluginResult result;

                            if (pixelHeightDiff > 100 && pixelHeightDiff != previousHeightDiff) {
                                result = new PluginResult(PluginResult.Status.OK, "S" + pixelHeightDiff);
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            } else if (pixelHeightDiff != previousHeightDiff && (previousHeightDiff - pixelHeightDiff) > 100) {
                                result = new PluginResult(PluginResult.Status.OK, "H");
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            }
                            previousHeightDiff = pixelHeightDiff;
                        }
                    };
                    rootView.getViewTreeObserver().addOnGlobalLayoutListener(list);
                }

                PluginResult dataResult = new PluginResult(PluginResult.Status.OK);
                dataResult.setKeepCallback(true);
                callbackContext.sendPluginResult(dataResult);
            });
            return true;
        }

        return false;
    }

    @Override
    public void onDestroy() {
        if (rootView != null && list != null) {
            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(list);
        }
        super.onDestroy();
    }
}