package com.example.catatanku;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.example.catatanku.adapters.NoteAdapter;
import com.example.catatanku.models.Note;
import com.example.catatanku.utils.Constants;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private RecyclerView recyclerView;
    private NoteAdapter noteAdapter;
    private List<Note> noteList;
    private RequestQueue requestQueue;
    private SharedPreferences sharedPreferences;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton addNoteFab;
    private TextView greetingTextView;
    private CircleImageView navProfileImage;
    private TextView navUsername;
    private TextView navEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // fungsi sharedPreferences: menyimpan data kecil secara permanen dalam format key-value.
        sharedPreferences = getSharedPreferences("CatatankuPrefs", MODE_PRIVATE);

        // req http,
        // newRequestQueue -> Membuat sistem antrean untuk request jaringan
        requestQueue = Volley.newRequestQueue(this);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        addNoteFab = findViewById(R.id.addNoteFab);
        greetingTextView = findViewById(R.id.greetingTextView);
        ImageView menu = findViewById(R.id.menu);

        // open side bar
        menu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        navigationView.setNavigationItemSelectedListener(this);

        // list catatan
        noteList = new ArrayList<>();
        noteAdapter = new NoteAdapter(this, noteList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(noteAdapter);

        // for swipe (refresh)
        swipeRefreshLayout.setOnRefreshListener(this::fetchNotes);

        // Floating add button
        addNoteFab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddEditNoteActivity.class);
            startActivity(intent);
        });

        // Profile Side bar
        View headerView = navigationView.getHeaderView(0);
        navProfileImage = headerView.findViewById(R.id.nav_profile_image);
        navUsername = headerView.findViewById(R.id.nav_username);
        navEmail = headerView.findViewById(R.id.nav_email);

        updateUserInfo();
        fetchNotes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // PENTING: Panggil metode ini setiap kali activity kembali aktif.
        // Ini memastikan data (nama, email, gambar profil) selalu terbaru
        // setelah pengguna kembali dari EditProfileActivity.
        updateUserInfo();
        fetchNotes();
    }


    private void updateUserInfo() {
        String userName = sharedPreferences.getString("userName", "User");
        String userEmail = sharedPreferences.getString("userEmail", "user@example.com");
        String profileImagePath = sharedPreferences.getString("userProfileImagePath", "");
        int userId = sharedPreferences.getInt("userId", -1);

        navUsername.setText(userName);
        navEmail.setText(userEmail);

        // photo profile
        if (!profileImagePath.isEmpty() && userId != -1) {
            String imageUrl = Constants.BASE_URL + "/profile/" + userId + "/image?t=" + System.currentTimeMillis();
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.baseline_account_circle_24) // Gambar default
                    .error(R.drawable.baseline_account_circle_24)     // Gambar default
                    .into(navProfileImage);
        } else {
            // Jika tidak ada path gambar, set gambar default secara manual
            navProfileImage.setImageResource(R.drawable.baseline_account_circle_24);
        }

        // (greeting text)
        String fullText = "Halo, " + userName + " 👋";
        SpannableString spannable = new SpannableString(fullText);
        int start = fullText.indexOf(userName);
        if (start != -1) {
            int end = start + userName.length();
            spannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorSecondary)),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        greetingTextView.setText(spannable);
    }


    // get note
    private void fetchNotes() {
        int userId = sharedPreferences.getInt("userId", -1);
        if (userId == -1) {
            Toast.makeText(this, "Sesi berakhir. Silakan login kembali.", Toast.LENGTH_SHORT).show();
            logout();
            return;
        }

        swipeRefreshLayout.setRefreshing(true); // Tampilkan indikator refresh
        String urlWithParams = Constants.BASE_URL + "/notes?user_id=" + userId;

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.GET, urlWithParams, null,
                response -> {
                    swipeRefreshLayout.setRefreshing(false);
                    try {
                        if (response.getBoolean("success")) {
                            noteList.clear(); // Hapus data lama sebelum mengisi yang baru
                            JSONArray dataArray = response.getJSONArray("data");
                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject noteObj = dataArray.getJSONObject(i);
                                Note note = new Note(
                                        noteObj.getInt("id"),
                                        noteObj.getString("title"),
                                        noteObj.getString("content"),
                                        noteObj.getString("created_at"),
                                        noteObj.optString("image_path", "")
                                );
                                noteList.add(note);
                            }
                            noteAdapter.notifyDataSetChanged();
                        } else {
                            String message = response.optString("message", "Gagal memuat catatan");
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    swipeRefreshLayout.setRefreshing(false);
                    String errorMessage = "Gagal memuat catatan. Periksa koneksi Anda.";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "UTF-8");
                            JSONObject jsonObject = new JSONObject(responseBody);
                            errorMessage = jsonObject.optString("message", errorMessage);
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing error response", e);
                        }
                    }
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
        );

        requestQueue.add(jsonObjectRequest);
    }

    // side bar nav menu
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_add_note) {
            startActivity(new Intent(MainActivity.this, AddEditNoteActivity.class));
        } else if (id == R.id.nav_edit_profile) {
            startActivity(new Intent(MainActivity.this, EditProfileActivity.class));
        } else if (id == R.id.nav_reset_pw) {
            startActivity(new Intent(MainActivity.this, ResetPasswordActivity.class));
        } else if (id == R.id.nav_logout) {
            logout();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

   // Logout
    private void logout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    // menutup sidebar dlu ketika lagi terbuka
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}