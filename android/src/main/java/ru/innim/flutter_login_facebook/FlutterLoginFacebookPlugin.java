package ru.innim.flutter_login_facebook;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.CallbackManager;
import com.facebook.login.LoginManager;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;

/** FlutterLoginFacebookPlugin */
public class FlutterLoginFacebookPlugin implements FlutterPlugin, ActivityAware,
        MethodCallHandler.LoginHost {
    private static final String _CHANNEL_NAME = "flutter_login_facebook";
    private static final String _METHOD_READY = "ready";

    private MethodChannel _dartChannel;

    private MethodCallHandler _methodCallHandler;
    private ActivityListener _activityListener;
    private CallbackManager _callbackManager;
    private ActivityPluginBinding _activityPluginBinding;
    private LoginCallback _loginCallback;
    @Nullable
    private Activity _cachedActivity;
    private boolean _callbacksRegistered = false;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        _unregisterCallbacks();

        final BinaryMessenger messenger = flutterPluginBinding.getBinaryMessenger();
        _dartChannel = new MethodChannel(messenger, _CHANNEL_NAME);
        _callbackManager = CallbackManager.Factory.create();
        _loginCallback = new LoginCallback();
        _activityListener = new ActivityListener(_callbackManager);
        _methodCallHandler = new MethodCallHandler(_loginCallback);
        _methodCallHandler.updateApplicationContext(flutterPluginBinding.getApplicationContext());
        _methodCallHandler.setActivityProvider(this::getActivity);
        _methodCallHandler.setLoginHost(this);
        _syncActivityWithHandler();
        if (_activityPluginBinding != null) {
            _registerCallbacks(_activityPluginBinding);
        }
        _dartChannel.setMethodCallHandler(_methodCallHandler);
        _dartChannel.invokeMethod(_METHOD_READY, null);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (_methodCallHandler != null) {
            _methodCallHandler.setActivityProvider(null);
            _methodCallHandler.setLoginHost(null);
            _methodCallHandler.updateActivity(null);
        }
        _methodCallHandler = null;
        _dartChannel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding activityPluginBinding) {
        _setActivity(activityPluginBinding);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        _unregisterCallbacks();
        if (_methodCallHandler != null) {
            _methodCallHandler.updateActivity(null);
        }
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding activityPluginBinding) {
        _setActivity(activityPluginBinding);
    }

    @Override
    public void onDetachedFromActivity() {
        _unregisterCallbacks();
        _activityPluginBinding = null;
        _cachedActivity = null;
        if (_methodCallHandler != null) {
            _methodCallHandler.updateActivity(null);
        }
    }

    @Nullable
    private Activity getActivity() {
        if (_cachedActivity != null) {
            return _cachedActivity;
        }
        if (_activityPluginBinding != null) {
            return _activityPluginBinding.getActivity();
        }
        return null;
    }

    private void _setActivity(@NonNull ActivityPluginBinding activityPluginBinding) {
        _activityPluginBinding = activityPluginBinding;
        _cachedActivity = activityPluginBinding.getActivity();
        _syncActivityWithHandler();
        _registerCallbacks(activityPluginBinding);
    }

    private void _syncActivityWithHandler() {
        if (_methodCallHandler != null) {
            _methodCallHandler.updateActivity(getActivity());
        }
    }

    @Override
    public void ensureCallbacksRegistered() {
        if (_activityPluginBinding != null) {
            _registerCallbacks(_activityPluginBinding, true);
        }
    }

    @Override
    public CallbackManager getCallbackManager() {
        return _callbackManager;
    }

    private void _registerCallbacks(@NonNull ActivityPluginBinding activityPluginBinding) {
        _registerCallbacks(activityPluginBinding, false);
    }

    private void _registerCallbacks(
            @NonNull ActivityPluginBinding activityPluginBinding,
            boolean force
    ) {
        if (_callbackManager == null || _loginCallback == null) {
            return;
        }

        if (_callbacksRegistered && !force) {
            return;
        }

        if (_callbacksRegistered) {
            LoginManager.getInstance().unregisterCallback(_callbackManager);
            activityPluginBinding.removeActivityResultListener(_activityListener);
        }

        LoginManager.getInstance().registerCallback(_callbackManager, _loginCallback);
        activityPluginBinding.addActivityResultListener(_activityListener);
        _callbacksRegistered = true;
    }

    private void _unregisterCallbacks() {
        if (!_callbacksRegistered || _activityPluginBinding == null) {
            return;
        }

        LoginManager.getInstance().unregisterCallback(_callbackManager);
        _activityPluginBinding.removeActivityResultListener(_activityListener);
        _callbacksRegistered = false;
    }
}
