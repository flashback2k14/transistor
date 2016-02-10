package org.y20k.transistor;

import android.app.Application;


public class AppApplication extends Application {

    private boolean mDebugEnabled;

    @Override
    public void onCreate() {
        super.onCreate();
        mDebugEnabled = true;
    }

    public boolean isDebugEnabled() {
        return mDebugEnabled;
    }
}
