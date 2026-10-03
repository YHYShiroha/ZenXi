package com.zenxi.yogatimer;

import android.app.Application;

public final class ZenApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Settings.get(this);
        SoundBank.get().init(this);
    }
}
