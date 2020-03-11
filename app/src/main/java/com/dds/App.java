package com.dds;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.squareup.leakcanary.LeakCanary;

/**
 * Created by dds on 2019/8/25.
 * android_shuai@163.com
 */
public class App extends Application {
    private static App app;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        FirebaseApp.initializeApp(this);
        LeakCanary.install(this);
    }

    public static App getInstance() {
        return app;
    }
}
