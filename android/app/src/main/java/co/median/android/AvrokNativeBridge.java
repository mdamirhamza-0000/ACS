package co.median.android;

import android.app.Activity;
import android.os.CancellationSignal;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.Credential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

/**
 * Native Google sign-in for the AVROK ACS web app.
 * JS calls AvrokNative.googleSignIn(webClientId); the result is delivered to
 * window.__avrokGoogleResult({success, idToken, error}).
 */
public class AvrokNativeBridge {
    private final Activity activity;
    private final WebView webView;

    public AvrokNativeBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void googleSignIn(final String serverClientId) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    CredentialManager credentialManager = CredentialManager.create(activity);

                    GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(serverClientId)
                            .setAutoSelectEnabled(false)
                            .build();

                    GetCredentialRequest request = new GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build();

                    credentialManager.getCredentialAsync(
                            activity,
                            request,
                            new CancellationSignal(),
                            ContextCompat.getMainExecutor(activity),
                            new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                                @Override
                                public void onResult(GetCredentialResponse response) {
                                    try {
                                        Credential credential = response.getCredential();
                                        if (credential instanceof CustomCredential
                                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                                            GoogleIdTokenCredential googleCredential =
                                                    GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                                            deliver(true, googleCredential.getIdToken(), null);
                                        } else {
                                            deliver(false, null, "Unexpected credential type: " + credential.getType());
                                        }
                                    } catch (Exception e) {
                                        deliver(false, null, "Could not read Google credential: " + e.getMessage());
                                    }
                                }

                                @Override
                                public void onError(GetCredentialException e) {
                                    deliver(false, null, e.getClass().getSimpleName() + ": " + e.getMessage());
                                }
                            });
                } catch (Exception e) {
                    deliver(false, null, "Native Google sign-in failed: " + e.getMessage());
                }
            }
        });
    }

    private void deliver(boolean success, String idToken, String error) {
        try {
            JSONObject json = new JSONObject();
            json.put("success", success);
            json.put("idToken", idToken == null ? JSONObject.NULL : idToken);
            json.put("error", error == null ? JSONObject.NULL : error);
            final String script = "window.__avrokGoogleResult && window.__avrokGoogleResult(" + json.toString() + ");";
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(script, null);
                }
            });
        } catch (Exception ignored) {
        }
    }
}
