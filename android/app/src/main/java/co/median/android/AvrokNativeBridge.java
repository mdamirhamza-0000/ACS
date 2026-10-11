package co.median.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

/**
 * Native Google sign-in for the AVROK ACS web app (centred account picker).
 * JS calls AvrokNative.googleSignIn(webClientId); the result is delivered to
 * window.__avrokGoogleResult({success, idToken, error}).
 */
public class AvrokNativeBridge {
    private static final int RC_SIGN_IN = 9001;
    private static final String FRAGMENT_TAG = "avrok_google_signin";

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
                    if (!(activity instanceof FragmentActivity)) {
                        deliver(false, null, "Activity does not support fragments");
                        return;
                    }
                    FragmentActivity fragmentActivity = (FragmentActivity) activity;

                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(serverClientId)
                            .requestEmail()
                            .build();
                    final GoogleSignInClient client = GoogleSignIn.getClient(activity, gso);

                    // Sign out first so the account picker is shown every time.
                    client.signOut().addOnCompleteListener(activity, new com.google.android.gms.tasks.OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(Task<Void> task) {
                            try {
                                SignInFragment fragment = new SignInFragment();
                                fragment.setIntentAndBridge(client.getSignInIntent(), AvrokNativeBridge.this);
                                fragmentActivity.getSupportFragmentManager()
                                        .beginTransaction()
                                        .add(fragment, FRAGMENT_TAG)
                                        .commitNow();
                            } catch (Exception e) {
                                deliver(false, null, "Could not open Google account picker: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    deliver(false, null, "Native Google sign-in failed: " + e.getMessage());
                }
            }
        });
    }

    void handleResult(Intent data) {
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken == null) {
                deliver(false, null, "Google did not return an ID token");
            } else {
                deliver(true, idToken, null);
            }
        } catch (ApiException e) {
            deliver(false, null, "Google sign-in error code " + e.getStatusCode());
        } catch (Exception e) {
            deliver(false, null, "Google sign-in failed: " + e.getMessage());
        }
    }

    void deliver(boolean success, String idToken, String error) {
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

    /** Headless fragment that launches the picker and receives its result. */
    public static class SignInFragment extends Fragment {
        private Intent signInIntent;
        private AvrokNativeBridge bridge;

        void setIntentAndBridge(Intent intent, AvrokNativeBridge bridge) {
            this.signInIntent = intent;
            this.bridge = bridge;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onStart() {
            super.onStart();
            if (signInIntent != null) {
                startActivityForResult(signInIntent, RC_SIGN_IN);
                signInIntent = null;
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == RC_SIGN_IN) {
                if (bridge != null) {
                    bridge.handleResult(data);
                }
                if (getFragmentManager() == null && getParentFragmentManager() != null) {
                    getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                } else if (getParentFragmentManager() != null) {
                    getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                }
            }
        }
    }
}
