package com.example.catatanku;

import android.Manifest; 
import android.content.Intent;
import android.content.SharedPreferences;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build; 
import android.net.Uri; // Untuk merepresentasikan alamat dari sebuah data (misal, gambar dari galeri).
import android.os.Bundle;
import android.provider.DocumentsContract; 
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView; // Komponen UI untuk menampilkan gambar.
import android.widget.LinearLayout; // Komponen UI untuk menata elemen secara linear.
import android.widget.Toast;

// Library Glide untuk memuat dan menampilkan gambar secara efisien.
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.example.catatanku.models.Note;
import com.example.catatanku.utils.Constants;
import com.example.catatanku.utils.MultipartRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class AddEditNoteActivity extends AppCompatActivity {
    private EditText titleEditText, contentEditText;
    private Button saveButton;
    private ImageView noteImageView; // Untuk menampilkan gambar catatan.
    private LinearLayout addImageContainer; // Layout yang berfungsi sebagai tombol untuk menambah gambar.
    private RequestQueue requestQueue;
    private SharedPreferences sharedPreferences;
    private Note noteToEdit;
    private boolean isEditMode = false;
    private static final int GALLERY_PERMISSION_CODE = 100; // untuk permintaan izin galeri.
    private File imageFileForUpload; // Variabel untuk menampung file gambar yang akan diunggah.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_note);

        sharedPreferences = getSharedPreferences("CatatankuPrefs", MODE_PRIVATE);
        requestQueue = Volley.newRequestQueue(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        titleEditText = findViewById(R.id.titleEditText);
        contentEditText = findViewById(R.id.contentEditText);
        saveButton = findViewById(R.id.saveButton);
        noteImageView = findViewById(R.id.noteImageView);
        addImageContainer = findViewById(R.id.addImageContainer);

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        try {
            if (getIntent() != null && getIntent().hasExtra("note")) {
                noteToEdit = getIntent().getParcelableExtra("note");
                if (noteToEdit != null) {
                    isEditMode = true;
                    titleEditText.setText(noteToEdit.getTitle());
                    contentEditText.setText(noteToEdit.getContent());
                    getSupportActionBar().setTitle("Ubah Catatan");

                    // Memeriksa apakah catatan memiliki path gambar (untuk ditampilkan saat mode edit).
                    if (noteToEdit.getImagePath() != null && !noteToEdit.getImagePath().isEmpty()) {
                        // Membuat URL lengkap untuk mengambil gambar dari server.
                        String imageUrl = Constants.BASE_URL + "/notes/" + noteToEdit.getId() + "/image";
                        // Menggunakan Glide untuk memuat gambar dari URL ke ImageView.
                        // ?t=... ditambahkan untuk mencegah caching gambar yang sama.
                        Glide.with(this)
                                .load(imageUrl + "?t=" + System.currentTimeMillis())
                                .diskCacheStrategy(DiskCacheStrategy.NONE) // Jangan simpan cache di disk.
                                .skipMemoryCache(true) // Jangan simpan cache di memori.
                                .placeholder(R.drawable.placeholder_image) // Gambar sementara saat memuat.
                                .error(R.drawable.placeholder_image) // Gambar jika terjadi error.
                                .into(noteImageView);
                        // Menampilkan ImageView dan menyembunyikan tombol "Tambah Gambar".
                        noteImageView.setVisibility(View.VISIBLE);
                        addImageContainer.setVisibility(View.GONE);
                    } else {
                        // Jika tidak ada gambar, sembunyikan ImageView dan tampilkan tombol "Tambah Gambar".
                        noteImageView.setVisibility(View.GONE);
                        addImageContainer.setVisibility(View.VISIBLE);
                    }
                } else {
                    showErrorAndFinish("Data catatan tidak valid");
                }
            } else {
                getSupportActionBar().setTitle("Tambah Catatan");
                // Sembunyikan ImageView dan tampilkan tombol "Tambah Gambar" untuk catatan baru.
                noteImageView.setVisibility(View.GONE);
                addImageContainer.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            showErrorAndFinish("Gagal memuat catatan: " + e.getMessage());
        }

        saveButton.setOnClickListener(v -> saveNote());

        // Membuat satu listener untuk membuka galeri.
        View.OnClickListener openGalleryListener = v -> checkPermissionAndOpenGallery();
        // Menerapkan listener tersebut ke kontainer "Tambah Gambar" dan ImageView itu sendiri.
        //  menambah gambar baru atau mengganti gambar yang sudah ada.
        addImageContainer.setOnClickListener(openGalleryListener);
        noteImageView.setOnClickListener(openGalleryListener);
    }


    private void saveNote() {
        String title = titleEditText.getText().toString().trim();
        String content = contentEditText.getText().toString().trim();
        int userId = getUserId();

        if (title.isEmpty()) {
            titleEditText.setError("Judul tidak boleh kosong");
            return;
        }

        if (userId == -1) {
            Toast.makeText(this, "Anda belum login", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String url = Constants.BASE_URL + (isEditMode ? "/notes/" + noteToEdit.getId() : "/notes");
        Map<String, String> params = new HashMap<>();
        params.put("title", title);
        params.put("content", content);
        params.put("user_id", String.valueOf(userId));

        // Menyiapkan bagian gambar untuk request multipart.
        MultipartRequest.Part imagePart = null;
        // Jika ada file gambar baru yang dipilih untuk diunggah (imageFileForUpload tidak null).
        if (imageFileForUpload != null && imageFileForUpload.exists()) {
            // Buat objek Part untuk gambar tersebut, yang akan dimasukkan ke dalam request.
            imagePart = new MultipartRequest.Part("image", "image/jpeg", imageFileForUpload);
        // Jika mode edit dan tidak ada gambar baru dipilih, cek apakah ada gambar lama.
        } else if (isEditMode && noteToEdit.getImagePath() != null && !noteToEdit.getImagePath().isEmpty()) {
            // Kirim path gambar yang ada sebagai parameter. Ini memberitahu backend untuk tidak menghapus gambar lama.
            params.put("existing_image", noteToEdit.getImagePath());
        }

        // Membuat objek MultipartRequest untuk mengirim data teks dan file (gambar) secara bersamaan.
        MultipartRequest request = new MultipartRequest(
                isEditMode ? Request.Method.PUT : Request.Method.POST,
                url,
                params,
                imagePart, // Menyertakan data file gambar jika ada.
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            if (response.has("data")) {
                                JSONObject data = response.getJSONObject("data");
                                Note noteResult = new Note(
                                        data.optInt("id"),
                                        data.optString("title"),
                                        data.optString("content"),
                                        data.optString("created_at"),
                                        data.optString("image_path") // Ambil path gambar baru jika ada.
                                );

                                if (isEditMode) {
                                    Intent resultIntent = new Intent();
                                    resultIntent.putExtra("updatedNote", noteResult);
                                    setResult(RESULT_OK, resultIntent);
                                    Toast.makeText(this, "Catatan berhasil diperbarui", Toast.LENGTH_SHORT).show();
                                } else {
                                    Intent intent = new Intent(this, NoteDetailActivity.class);
                                    intent.putExtra("note", noteResult);
                                    startActivity(intent);
                                    Toast.makeText(this, "Catatan berhasil dibuat", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                setResult(RESULT_OK);
                            }
                            finish();
                        } else {
                            String message = response.getString("message");
                            Toast.makeText(this, "Error: " + message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Error parsing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },

                error -> {
                    //
                }
        );
        String token = getSharedPreferences("CatatankuPrefs", MODE_PRIVATE).getString("token", null);
        if (token != null) {
            request.setHeader("Authorization", "Bearer " + token);
        }
        request.setRetryPolicy(new DefaultRetryPolicy(30000, 3, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    private int getUserId() {
        return sharedPreferences.getInt("userId", -1);
    }

    // Metode untuk memproses dan menampilkan gambar yang dipilih dari galeri.
    private void processAndDisplayImage(Uri imageUri) throws IOException {
        // Menggunakan Glide untuk menampilkan pratinjau gambar yang dipilih di ImageView.
        Glide.with(this).load(imageUri).into(noteImageView);
        // Menampilkan ImageView dan menyembunyikan tombol "Tambah Gambar".
        noteImageView.setVisibility(View.VISIBLE);
        addImageContainer.setVisibility(View.GONE);

        // Mengubah Uri gambar menjadi file fisik yang bisa diunggah ke server.
        imageFileForUpload = createTempFileFromUri(imageUri);
        // Jika gagal membuat file, beri tahu pengguna.
        if (imageFileForUpload == null) {
            Toast.makeText(this, "Gagal mempersiapkan file untuk diunggah.", Toast.LENGTH_SHORT).show();
        }
    }

    // Metode untuk membuat file sementara dari sebuah Uri. File ini yang akan diunggah.
    private File createTempFileFromUri(Uri uri) {
        try {
            // Membuka InputStream dari Uri gambar yang dipilih dari galeri.
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            // Membuat file sementara di direktori cache aplikasi dengan nama unik.
            File tempFile = File.createTempFile("upload_img_", ".jpg", getCacheDir());
            tempFile.deleteOnExit();

            // Menyalin data dari InputStream (gambar dari galeri) ke FileOutputStream (file sementara).
            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
            // Mengembalikan file sementara yang siap untuk diunggah.
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    // Menggunakan API modern untuk menangani hasil dari pemilih galeri.
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Callback ini dieksekusi setelah pengguna memilih gambar dari galeri dan kembali ke aplikasi.
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // Mengambil Uri dari gambar yang dipilih.
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        try {
                            // Memproses dan menampilkan gambar.
                            processAndDisplayImage(selectedImageUri);
                        } catch (IOException e) {
                            Toast.makeText(this, "Gagal memproses gambar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Gagal memilih gambar", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    // Metode untuk memeriksa izin akses galeri dan kemudian membukanya.
    @SuppressLint("InlinedApi")
    private void checkPermissionAndOpenGallery() {
        // Menentukan izin yang diperlukan berdasarkan versi Android.
        // Android 13+ : READ_MEDIA_IMAGES. Di bawahnya: READ_EXTERNAL_STORAGE.
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        // Memeriksa apakah izin sudah diberikan oleh pengguna.
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            // Jika belum, minta izin kepada pengguna.
            ActivityCompat.requestPermissions(this, new String[]{permission}, GALLERY_PERMISSION_CODE);
        } else {
            // Jika izin sudah ada, buat Intent untuk membuka galeri.
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); // Hanya file yang bisa dibuka.
            intent.setType("image/*"); // Filter hanya untuk tipe file gambar.
            
            // Meluncurkan pemilih galeri menggunakan launcher yang sudah dibuat.
            galleryLauncher.launch(intent);
        }
    }

    // Callback yang dipanggil setelah pengguna merespons permintaan izin.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Memeriksa apakah ini adalah respons untuk permintaan izin galeri.
        if (requestCode == GALLERY_PERMISSION_CODE) {
            // Memeriksa apakah izin diberikan.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Jika ya, panggil lagi metode untuk membuka galeri.
                checkPermissionAndOpenGallery();
            } else {
                // Jika tidak, beri tahu pengguna bahwa izin diperlukan.
                Toast.makeText(this, "Izin akses galeri dibutuhkan untuk memilih gambar", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}