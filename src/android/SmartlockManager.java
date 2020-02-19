package org.apache.cordova.smartlock;

import android.content.Intent;
import android.content.IntentSender;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.auth.api.credentials.CredentialsOptions;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

public class SmartlockManager extends AppCompatActivity {

    private static final String TAG = "SmartlockManager";
    private static final int RC_READ = 11;
    private static final int RC_SAVE = 10;

    private CallbackContext mCallbackContext = null;
    private GoogleApiClient googleApiClient;
    private boolean mIsResolving = false;

    protected void initialize(CallbackContext callbackContext) {
        this.mCallbackContext = callbackContext;
        CredentialsOptions credentialsOptions = new CredentialsOptions.Builder()
                .forceEnableSaveDialog()
                .build();

        googleApiClient = new GoogleApiClient.Builder(cordova.getActivity())
                .addApi(Auth.CREDENTIALS_API, credentialsOptions)
                .build();
    }

    public void executeRequest() {
        CredentialRequest credentialRequest = new CredentialRequest.Builder()
                .setPasswordLoginSupported(true)
                .build();

        Auth.CredentialsApi.request(googleApiClient, credentialRequest)
                .setResultCallback(new ResultCallback<CredentialRequestResult>() {
                    @Override
                    public void onResult(@NonNull CredentialRequestResult credentialRequestResult) {
                        Status status = credentialRequestResult.getStatus();
                        Log.d(TAG, "Get status " + credentialRequestResult);
                        if (status.isSuccess()) {
                            // SUCCESS REQUEST TODO
                            credentialRequestResult.getCredential();
                        } else if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED) {
                            resolveResult(status, RC_READ);
                        } else if (status.getStatusCode() == CommonStatusCodes.SIGN_IN_REQUIRED) {
                            Log.d(TAG, "Sign in required");
                            // ERROR REQUEST (Empty, not found cases)
                            sendError(PluginError.SMARTLOCK__REQUEST__ACCOUNTS_NOT_FOUND);
                        } else {
                            Log.w(TAG, "Unrecognized status code: " + status.getStatusCode());
                            // ERROR REQUEST (Unknown)
                            sendError(
                                    PluginError.SMARTLOCK__COMMON__UNKOWN.getValue(),
                                    status.getStatusCode() + " - " + status.getStatusMessage());
                        }
                    }
                });
    }

    public void executeSave(Credential credential) {
        Auth.CredentialsApi.save(googleApiClient, credential)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.d(TAG, "Credential saved");
                            // SUCCESS SAVE TODO

                        } else {
                            resolveResult(status, RC_SAVE);
                        }
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "Result code: " + resultCode);

        switch (requestCode) {
            case RC_READ:
                if (resultCode == RESULT_OK) {
                    // SUCCESS REQUEST TODO
                    Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                } else {
                    Log.d(TAG, "Request failed");
                    // ERROR REQUEST
                    sendError(PluginError.SMARTLOCK__REQUEST__DIALOG_CANCELLED);
                }
                break;
            case RC_SAVE:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Credential Save: OK");
                    // SUCCESS SAVE TODO
                } else {
                    Log.e(TAG, "Credential Save Failed");
                    // ERROR SAVE
                    sendError(PluginError.SMARTLOCK__SAVE);
                }
                break;
        }

        mIsResolving = false;
    }

    private void resolveResult(Status status, int requestCode) {
        if (mIsResolving) {
            Log.w(TAG, "resolveResult: already resolving.");
            return;
        }

        Log.d(TAG, "Resolving: " + status);
        if (status.hasResolution()) {
            try {
                status.startResolutionForResult(cordova.getActivity(), requestCode);
                mIsResolving = true;
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, "STATUS: Failed to send resolution.", e);
                // ERROR (WEIRD) Resolution REQUEST/SAVE
                sendError(PluginError.SMARTLOCK__COMMON__RESOLUTION_PROMPT_FAIL);
            }
        } else {
            sendError(
                    PluginError.SMARTLOCK__COMMON__UNKOWN.getValue(),
                    status.getStatusCode() + " - " + status.getStatusMessage());
        }
    }

    public void sendEmptySuccess() {
        Log.e(TAG, "Empty success");
        cordova.getActivity().runOnUiThread(() -> this.mCallbackContext.success());
    }

    public void sendRequestSuccess(String message) {
        Log.e(TAG, "Request Success" + message);
        cordova.getActivity().runOnUiThread(() -> this.mCallbackContext.success(message));
    }

    public void sendError(PluginError error) {
        sendError(error.getValue(), error.getMessage());
    }

    public void sendError(int code, String message) {
        JSONObject resultJson = new JSONObject();
        try {
            resultJson.put("code", code);
            resultJson.put("message", message);

            PluginResult result = new PluginResult(PluginResult.Status.ERROR, resultJson);
            cordova.getActivity().runOnUiThread(() ->
                    this.mCallbackContext.sendPluginResult(result));
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

}