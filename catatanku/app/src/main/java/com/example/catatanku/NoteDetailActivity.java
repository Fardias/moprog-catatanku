package com.example.catatanku;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.catatanku.models.Note;
import com.example.catatanku.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NoteDetailActivity extends AppCompatActivity {

    private TextView titleTextView, contentTextView, dateTextView;
    private ImageView noteImageView;
    private Note note;
    private RequestQueue requestQueue;
    private SharedPreferences sharedPreferences;

    private final ActivityResultLauncher<Intent> editNoteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Note updatedNote = result.getData().getParcelableExtra("updatedNote");
                    if (updatedNote != null) {
                        this.note = updatedNote;
                        displayNote();
                        setResult(RESULT_OK);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        sharedPreferences = getSharedPreferences("CatatankuPrefs", MODE_PRIVATE);
        requestQueue = Volley.newRequestQueue(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Detail Catatan");
        }

        titleTextView = findViewById(R.id.titleTextView);
        contentTextView = findViewById(R.id.contentTextView);
        dateTextView = findViewById(R.id.dateTextView);
        noteImageView = findViewById(R.id.noteImageView);

        if (getIntent() != null && getIntent().hasExtra("note")) {
            note = getIntent().getParcelableExtra("note");
            if (note != null) {
                displayNote(); // Langsung tampilkan data yang dibawa dari list
            } else {
                showErrorAndFinish("Data catatan tidak valid.");
            }
        } else {
            showErrorAndFinish("Catatan tidak ditemukan.");
        }
    }

    private void displayNote() {
        if (note == null) {
            showErrorAndFinish("Catatan tidak valid.");
            return;
        }

        titleTextView.setText(note.getTitle());
        contentTextView.setText(note.getContent());
        dateTextView.setText(String.format("Dibuat pada: %s", note.getCreatedAt()));

        String imagePath = note.getImagePath();
        Log.d("NoteDetail", "Image Path from Note object: " + imagePath);

        if (imagePath != null && !imagePath.isEmpty()) {
            String imageUrl = Constants.BASE_URL + "/notes/" + note.getId() + "/image?t=" + System.currentTimeMillis();
            Log.d("NoteDetail", "Loading image from URL: " + imageUrl);

            noteImageView.setVisibility(View.VISIBLE);

            Glide.with(this)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(noteImageView);
        } else {
            noteImageView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.note_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_edit) {
            Intent intent = new Intent(this, AddEditNoteActivity.class);
            intent.putExtra("note", note);
            editNoteLauncher.launch(intent); // Gunakan launcher modern
            return true;
        } else if (id == R.id.action_delete) {
            showDeleteConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Catatan")
                .setMessage("Apakah Anda yakin ingin menghapus catatan ini?")
                .setPositiveButton("Hapus", (dialog, which) -> deleteNote())
                .setNegativeButton("Batal", null)
                .show();
    }

    private void deleteNote() {
        String token = getToken();
        if (token == null) {
            Toast.makeText(this, "Sesi berakhir, silakan login kembali.", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = Constants.BASE_URL + "/notes/" + note.getId();
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(this, "Catatan berhasil dihapus", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK); // Kirim sinyal ke activity sebelumnya bahwa ada perubahan
                            finish();
                        } else {
                            Toast.makeText(this, response.optString("message", "Gagal menghapus"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Gagal memproses respons server", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e("NoteDetail", "Delete Error: " + error.toString());
                    Toast.makeText(this, "Terjadi kesalahan saat menghapus catatan", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private String getToken() {
        return sharedPreferences.getString("token", null);
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }
}