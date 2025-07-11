package com.example.catatanku;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import android.os.Build;
import android.provider.DocumentsContract;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.example.catatanku.utils.Constants;
import com.example.catatanku.utils.MultipartRequest;

import org.json.JSONException;
import org.json.JSONObject;

import de.hdodenhof.circleimageview.CircleImageView;

public class EditProfileActivity extends AppCompatActivity {

    private EditText nameEditText, emailEditText;
    private Button saveProfileButton;
    private CircleImageView profileImageView;
    private SharedPreferences sharedPreferences;
    private RequestQueue requestQueue;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        sharedPreferences = getSharedPreferences("CatatankuPrefs", MODE_PRIVATE);
        requestQueue = Volley.newRequestQueue(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        saveProfileButton = findViewById(R.id.saveProfileButton);
        profileImageView = findViewById(R.id.profileImageView);

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        Glide.with(this).load(selectedImageUri).into(profileImageView);
                    }
                });

        profileImageView.setOnClickListener(v -> openGallery());
        saveProfileButton.setOnClickListener(v -> attemptUpdateProfile());
        loadUserProfile();
    }



    private void openGallery() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);


        intent.addCategory(Intent.CATEGORY_OPENABLE);


        intent.setType("image/*");


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download"
            );
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }


        galleryLauncher.launch(intent);
    }


    private void loadUserProfile() {
        String currentName = sharedPreferences.getString("userName", "");
        String currentEmail = sharedPreferences.getString("userEmail", "");
        String profileImagePath = sharedPreferences.getString("userProfileImagePath", "");
        int userId = sharedPreferences.getInt("userId", -1);

        nameEditText.setText(currentName);
        emailEditText.setText(currentEmail);

        if (!profileImagePath.isEmpty() && userId != -1) {
            String imageUrl = Constants.BASE_URL + "/profile/" + userId + "/image?t=" + System.currentTimeMillis();
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.baseline_account_circle_24)
                    .error(R.drawable.baseline_account_circle_24)
                    .into(profileImageView);
        }
    }


    private void attemptUpdateProfile() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Nama dan Email tidak boleh kosong", Toast.LENGTH_SHORT).show();
            return;
        }


        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("user_id", String.valueOf(sharedPreferences.getInt("userId", -1)));
        params.put("name", name);
        params.put("email", email);


        MultipartRequest.Part filePart = null;
        if (selectedImageUri != null) {
            try {

                Bitmap bitmap = ((BitmapDrawable) profileImageView.getDrawable()).getBitmap();

                filePart = new MultipartRequest.Part("image", "image/jpeg", bitmap);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String url = Constants.BASE_URL + "/profile";


        MultipartRequest multipartRequest = new MultipartRequest(
                Request.Method.PUT,
                url,
                params,
                filePart,
                response -> {

                    try {
                        boolean success = response.optBoolean("success");
                        String message = response.optString("message", "Operasi selesai");
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                        if (success && response.has("user")) {
                            JSONObject updatedUser = response.getJSONObject("user");
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("userName", updatedUser.getString("name"));
                            editor.putString("userEmail", updatedUser.getString("email"));
                            editor.putString("userProfileImagePath", updatedUser.optString("profile_image_path", ""));
                            editor.apply();
                            finish();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Gagal memproses respons server", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    // Logika error tetap sama
                    String message = "Terjadi kesalahan jaringan";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject data = new JSONObject(responseBody);
                            message = data.optString("message", message);
                        } catch (Exception e) {
                            Log.e("EditProfile", "Error parsing error response", e);
                        }
                    }
                    Log.e("EditProfile", "Volley Error: " + error.toString());
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        );

        requestQueue.add(multipartRequest);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}