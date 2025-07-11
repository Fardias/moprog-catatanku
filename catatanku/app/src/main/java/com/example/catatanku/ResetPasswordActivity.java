package com.example.catatanku;

import android.os.Bundle;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class ResetPasswordActivity extends AppCompatActivity {

//        private static final String BASE_URL = "http://10.0.2.2:5000/api/password-reset/";
    private static final String BASE_URL = "http://10.255.235.232:5000/api/password-reset/";

    private LinearLayout requestLayout, resetLayout;
    private EditText etEmail, etToken, etNewPassword, etConfirmPassword;
    private Button btnRequestToken, btnResetPassword;
    private ProgressBar progressBar;
    private RequestQueue requestQueue;

    private String resetToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        requestQueue = Volley.newRequestQueue(this);

        requestLayout = findViewById(R.id.requestLayout);
        resetLayout = findViewById(R.id.resetLayout);
        etEmail = findViewById(R.id.etEmail);
        etToken = findViewById(R.id.etToken);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRequestToken = findViewById(R.id.btnRequestToken);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        progressBar = findViewById(R.id.progressBar);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        btnRequestToken.setOnClickListener(v -> handleRequestToken());
        btnResetPassword.setOnClickListener(v -> handleResetPassword());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleRequestToken() {
        String email = etEmail.getText().toString().trim();

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Please enter a valid email address.");
            return;
        }

        setLoading(true);

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("email", email);
        } catch (JSONException e) {
            e.printStackTrace();
            setLoading(false);
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, BASE_URL + "request", requestBody,
                response -> {
                    setLoading(false);
                    try {
                        if (response.has("token")) {
                            this.resetToken = response.getString("token");
                            // etToken.setText(resetToken); // Baris ini tidak lagi diperlukan
                            showToast("Reset code received. Please enter a new password.");

                            requestLayout.setVisibility(View.GONE);
                            resetLayout.setVisibility(View.VISIBLE);
                        } else {
                            showToast(response.getString("message"));
                            requestLayout.setVisibility(View.GONE);
                            resetLayout.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        showToast("An error occurred parsing the response.");
                    }
                },
                error -> {
                    setLoading(false);
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject data = new JSONObject(responseBody);
                            showToast(data.optString("message", "An unknown error occurred."));
                        } catch (Exception e) {
                            showToast("An unknown error occurred.");
                        }
                    } else {
                        showToast("Error: " + Objects.requireNonNull(error.getMessage()));
                    }
                });

        requestQueue.add(jsonObjectRequest);
    }

    private void handleResetPassword() {
        String token = this.resetToken;

        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (token == null || token.isEmpty()) { // Validasi token dari variabel
            showToast("Reset token is missing. Please request a new one.");
            return;
        }
        if (newPassword.isEmpty() || newPassword.length() < 6) {
            showToast("Password must be at least 6 characters long.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            showToast("Passwords do not match.");
            return;
        }

        setLoading(true);

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", token); // Menggunakan token dari variabel
            requestBody.put("password", newPassword);
        } catch (JSONException e) {
            e.printStackTrace();
            setLoading(false);
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, BASE_URL + "reset", requestBody,
                response -> {
                    setLoading(false);
                    try {
                        showToast(response.getString("message"));
                        if (response.getBoolean("success")) {
                            finish();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        showToast("An error occurred parsing the response.");
                    }
                },
                error -> {
                    setLoading(false);
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject data = new JSONObject(responseBody);
                            showToast(data.getString("message"));
                        } catch (Exception e) {
                            showToast("An unknown error occurred.");
                        }
                    } else {
                        showToast("Error: " + error.getMessage());
                    }
                });

        requestQueue.add(jsonObjectRequest);
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnRequestToken.setEnabled(false);
            btnResetPassword.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnRequestToken.setEnabled(true);
            btnResetPassword.setEnabled(true);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}