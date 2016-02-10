package org.y20k.transistor.helpers;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;


public class Logger {
    private Activity mActivity;
    private boolean mEnabled;
    private String mTag;

    public Logger(Activity activity, boolean enable, String tag) {
        this.mActivity = activity;
        this.mEnabled = enable;
        this.mTag = tag;
    }

    public void debug(String message, boolean showToast) {
        if (mEnabled)
            Log.e(mTag, message);
        if (showToast)
            Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
    }

    public void verbose(String message, boolean showToast) {
        if (mEnabled)
            Log.v(mTag, message);
        if (showToast)
            Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
    }

    public void error(String message, boolean showToast) {
        if (mEnabled)
            Log.e(mTag, message);
        if (showToast)
            Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
    }
}
