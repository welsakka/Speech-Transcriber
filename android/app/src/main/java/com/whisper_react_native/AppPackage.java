package com.whisper_react_native;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import com.whisper_react_native.modules.AudioModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppPackage implements ReactPackage {

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();

        /*
        Add all new modules here
         */

        modules.add(new AudioModule(reactContext));

        return modules;
    }
}
