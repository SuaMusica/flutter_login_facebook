package ru.innim.flutter_login_facebook;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultRegistryOwner;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookRequestError;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.LoginStatusCallback;
import com.facebook.Profile;
import com.facebook.login.LoginManager;

import org.json.JSONObject;

import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;

public class MethodCallHandler implements MethodChannel.MethodCallHandler {
    private final static String _LOGIN_METHOD = "logIn";
    private final static String _LOGOUT_METHOD = "logOut";
    private final static String _EXPRESS_LOGIN_METHOD = "expressLogIn";
    private final static String _GET_ACCESS_TOKEN = "getAccessToken";
    private final static String _GET_USER_PROFILE = "getUserProfile";
    private final static String _GET_SDK_VERSION = "getSdkVersion";
    private final static String _GET_USER_EMAIL = "getUserEmail";
    private final static String _GET_PROFILE_IMAGE_URL = "getProfileImageUrl";
    private final static String _IS_READY_METHOD = "isReady";

    private final static String _PERMISSIONS_ARG = "permissions";
    private final static String _WIDTH_ARG = "width";
    private final static String _HEIGHT_ARG = "height";

    private final LoginCallback _loginCallback;
    private Activity _activity;
    private Context _applicationContext;
    private ActivityProvider _activityProvider;
    private LoginHost _loginHost;

    interface ActivityProvider {
        Activity getActivity();
    }

    interface LoginHost {
        void ensureCallbacksRegistered();

        CallbackManager getCallbackManager();
    }

    public MethodCallHandler(LoginCallback loginCallback) {
        _loginCallback = loginCallback;
    }

    public void setActivityProvider(ActivityProvider activityProvider) {
        _activityProvider = activityProvider;
    }

    public void setLoginHost(LoginHost loginHost) {
        _loginHost = loginHost;
    }

    public void updateActivity(Activity activity) {
        _activity = activity;
    }

    public void updateApplicationContext(Context applicationContext) {
        _applicationContext = applicationContext.getApplicationContext();
    }

    private Context getApplicationContext() {
        final Activity activity = getActivity();
        if (activity != null) {
            return activity.getApplicationContext();
        }
        return _applicationContext;
    }

    private Activity getActivity() {
        if (_activity != null) {
            return _activity;
        }
        if (_activityProvider != null) {
            return _activityProvider.getActivity();
        }
        return null;
    }

    private void ensureFacebookSdkInitialized() {
        if (FacebookSdk.isInitialized()) {
            return;
        }

        final Context context = getApplicationContext();
        if (context == null) {
            throw new IllegalStateException("Application context is not available");
        }

        FacebookSdk.setAutoInitEnabled(true);
        FacebookSdk.sdkInitialize(context);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (_IS_READY_METHOD.equals(call.method)) {
            isReady(result);
            return;
        }

        switch (call.method) {
            case _LOGOUT_METHOD:
                logOut(result);
                return;
            case _GET_ACCESS_TOKEN:
                getAccessToken(result);
                return;
            case _GET_USER_PROFILE:
                getUserProfile(result);
                return;
            case _GET_SDK_VERSION:
                getSdkVersion(result);
                return;
            case _GET_USER_EMAIL:
                getUserEmail(result);
                return;
            case _GET_PROFILE_IMAGE_URL:
                final Integer width = call.argument(_WIDTH_ARG);
                final Integer height = call.argument(_HEIGHT_ARG);

                if (width != null && height != null ) {
                    getProfileImageUrl(result, width, height);
                } else {
                    result.error(ErrorCode.INVALID_ARGS, "Some of args is invalid", null);
                }
                return;
        }

        if (getActivity() == null) {
            result.error(ErrorCode.FAILED, "Activity is not available", null);
            return;
        }

        switch (call.method) {
            case _LOGIN_METHOD:
                final List<String> permissions = call.argument(_PERMISSIONS_ARG);
                logIn(permissions, result);
                break;
            case _EXPRESS_LOGIN_METHOD:
                expressLogin(result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void logIn(List<String> permissions, Result result) {
        final Activity activity = getActivity();
        if (activity == null) {
            result.error(ErrorCode.FAILED, "Activity is not available", null);
            return;
        }

        try {
            ensureFacebookSdkInitialized();
            if (_loginHost != null) {
                _loginHost.ensureCallbacksRegistered();
            }
            _loginCallback.addPending(result);

            if (_loginHost != null && activity instanceof ActivityResultRegistryOwner) {
                LoginManager.getInstance().logIn(
                        (ActivityResultRegistryOwner) activity,
                        _loginHost.getCallbackManager(),
                        permissions
                );
            } else {
                LoginManager.getInstance().logIn(activity, permissions);
            }
        } catch (Exception e) {
            result.error(ErrorCode.FAILED, e.getMessage(), null);
        }
    }

    private void expressLogin(final Result result) {
        final Activity activity = getActivity();
        if (activity == null) {
            result.error(ErrorCode.FAILED, "Activity is not available", null);
            return;
        }

        try {
            ensureFacebookSdkInitialized();
            LoginManager.getInstance().retrieveLoginStatus(activity.getApplicationContext(), new LoginStatusCallback() {
            @Override
            public void onCompleted(AccessToken token) {
                result.success(Results.loginSuccess(token));
            }
            @Override
            public void onFailure() {
                result.success(Results.loginCancel());
            }
            @Override
            public void onError(Exception e) {
                result.error(ErrorCode.FAILED, e.getMessage(), null);
            }
        });
        } catch (Exception e) {
            result.error(ErrorCode.FAILED, e.getMessage(), null);
        }
    }

    private void logOut(Result result) {
        try {
            if (!FacebookSdk.isInitialized()) {
                result.success(null);
                return;
            }
            ensureFacebookSdkInitialized();
            LoginManager.getInstance().logOut();
            result.success(null);
        } catch (Exception e) {
            result.error(ErrorCode.FAILED, e.getMessage(), null);
        }
    }

    private void getAccessToken(Result result) {
        ensureFacebookSdkInitialized();
        final AccessToken token = AccessToken.getCurrentAccessToken();
        result.success(Results.accessToken(token));
    }

    private void getUserProfile(Result result) {
        ensureFacebookSdkInitialized();
        final Profile profile = Profile.getCurrentProfile();
        result.success(Results.userProfile(profile));
    }

    private void getUserEmail(final Result result) {
        ensureFacebookSdkInitialized();
        GraphRequest request = GraphRequest.newMeRequest(AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        final FacebookRequestError error = response.getError();
                        if (error == null) {
                            try {
                                result.success(object.getString("email"));
                            } catch (Exception e) {
                                result.error(ErrorCode.UNKNOWN, e.getMessage(), null);
                            }
                        } else {
                            result.error(ErrorCode.FAILED, error.getErrorMessage(), null);
                        }
                    }
                });
        Bundle parameters = new Bundle();
        parameters.putString("fields", "email");
        request.setParameters(parameters);
        request.executeAsync();
    }

    private void getProfileImageUrl(Result result, int width, int height) {
        ensureFacebookSdkInitialized();
        final Profile profile = Profile.getCurrentProfile();
        final Uri uri = profile.getProfilePictureUri(width, height);
        if (uri != null) {
            result.success(uri.toString());
        } else {
            result.success(null);
        }
    }

    private void getSdkVersion(Result result) {
        ensureFacebookSdkInitialized();
        result.success(FacebookSdk.getSdkVersion());
    }

    private void isReady(Result result) {
        result.success(true);
    }
}
