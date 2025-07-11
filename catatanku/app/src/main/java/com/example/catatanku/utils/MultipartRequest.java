package com.example.catatanku.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class MultipartRequest extends Request<JSONObject> {
    private static final String BOUNDARY = "Boundary-" + System.currentTimeMillis();
    private static final String LINE_FEED = "\r\n";
    
    private final Map<String, String> params;
    private final Part filePart;
    private final Response.Listener<JSONObject> listener;
    private final Map<String, String> headers;

    public MultipartRequest(
        int method,
        String url,
        Map<String, String> params,
        Part filePart,
        Response.Listener<JSONObject> listener,
        Response.ErrorListener errorListener
    ) {
        super(method, url, errorListener);
        this.params = params != null ? params : new HashMap<>();
        this.filePart = filePart;
        this.listener = listener;
        this.headers = new HashMap<>();
    }

    public static class Part {
        private final String name;
        private final String type;
        private Bitmap bitmap;
        private File file;
        private final String fileName;
        private final byte[] fileData;

        // Constructor for Bitmap
        public Part(String name, String type, Bitmap bitmap) {
            this.name = name;
            this.type = type;
            this.bitmap = bitmap;
            this.file = null;
            this.fileName = "image_" + System.currentTimeMillis() + ".jpg";
            this.fileData = null;
        }

        // Constructor for File
        public Part(String name, String type, File file) {
            this.name = name;
            this.type = type;
            this.bitmap = null;
            this.file = file;
            this.fileName = file.getName();
            this.fileData = null;
        }

        // Constructor for byte array
        public Part(String name, String type, String fileName, byte[] fileData) {
            this.name = name;
            this.type = type;
            this.bitmap = null;
            this.file = null;
            this.fileName = fileName;
            this.fileData = fileData;
        }
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        headers.putAll(this.headers);
        return headers;
    }

    public void setHeader(String key, String value) {
        this.headers.put(key, value);
    }

    @Override
    protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
        try {
            String jsonString = new String(response.data, "UTF-8");
            return Response.success(new JSONObject(jsonString),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (Exception e) {
            return Response.error(new com.android.volley.ParseError(e));
        }
    }

    @Override
    protected void deliverResponse(JSONObject response) {
        listener.onResponse(response);
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            // Add text parameters
            for (Map.Entry<String, String> entry : params.entrySet()) {
                addFormField(bos, entry.getKey(), entry.getValue());
            }

            // Add file part if exists
            if (filePart != null) {
                if (filePart.bitmap != null) {
                    addBitmapPart(bos, filePart.name, filePart.fileName, filePart.bitmap);
                } else if (filePart.file != null && filePart.file.exists()) {
                    addFilePart(bos, filePart.name, filePart.fileName, filePart.file);
                } else if (filePart.fileData != null) {
                    addFileDataPart(bos, filePart.name, filePart.fileName, filePart.fileData);
                }
            }

            // Add end boundary
            bos.write(("--" + BOUNDARY + "--" + LINE_FEED).getBytes());
            return bos.toByteArray();
        } catch (IOException e) {
            Log.e("MultipartRequest", "Error creating request body", e);
            return null;
        } finally {
            try {
                bos.close();
            } catch (IOException e) {
                Log.e("MultipartRequest", "Error closing output stream", e);
            }
        }
    }
    
    private void addFilePart(ByteArrayOutputStream outputStream, String fieldName, String fileName, File file) throws IOException {
        outputStream.write( ("--" + BOUNDARY + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Type: application/octet-stream" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Transfer-Encoding: binary" + LINE_FEED + LINE_FEED).getBytes() );
        
        // Read file in chunks to avoid OOM
        byte[] buffer = new byte[4096];
        int bytesRead;
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        outputStream.write(LINE_FEED.getBytes());
        outputStream.flush();
    }
    
    private void addBitmapPart(ByteArrayOutputStream outputStream, String fieldName, String fileName, Bitmap bitmap) throws IOException {
        outputStream.write( ("--" + BOUNDARY + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Type: image/jpeg" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Transfer-Encoding: binary" + LINE_FEED + LINE_FEED).getBytes() );
        
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream);
        outputStream.write(byteArrayOutputStream.toByteArray());
        byteArrayOutputStream.close();
        
        outputStream.write(LINE_FEED.getBytes());
        outputStream.flush();
    }
    
    private void addFileDataPart(ByteArrayOutputStream outputStream, String fieldName, String fileName, byte[] fileData) throws IOException {
        outputStream.write( ("--" + BOUNDARY + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Type: application/octet-stream" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Transfer-Encoding: binary" + LINE_FEED + LINE_FEED).getBytes() );
        
        outputStream.write(fileData);
        outputStream.write(LINE_FEED.getBytes());
        outputStream.flush();
    }

    private void addFormField(ByteArrayOutputStream outputStream, String name, String value) throws IOException {
        outputStream.write(("--" + BOUNDARY + LINE_FEED).getBytes());
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"" + LINE_FEED).getBytes());
        outputStream.write(("Content-Type: text/plain; charset=UTF-8" + LINE_FEED).getBytes());
        outputStream.write(LINE_FEED.getBytes());
        outputStream.write((value + LINE_FEED).getBytes());
    }

    private void addBitmapPart(ByteArrayOutputStream outputStream, Part part) throws IOException {
        outputStream.write( ("--" + BOUNDARY + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Disposition: form-data; name=\"" + part.name + "\"; filename=\"" + part.fileName + "\"" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Type: " + part.type + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Transfer-Encoding: binary" + LINE_FEED + LINE_FEED).getBytes() );

        ByteArrayOutputStream bitmapStream = new ByteArrayOutputStream();
        part.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bitmapStream);
        outputStream.write(bitmapStream.toByteArray());
        outputStream.write(LINE_FEED.getBytes());
    }

    private void addFilePart(ByteArrayOutputStream outputStream, Part part) throws IOException {
        outputStream.write( ("--" + BOUNDARY + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Disposition: form-data; name=\"" + part.name + "\"; filename=\"" + part.fileName + "\"" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Type: " + part.type + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Transfer-Encoding: binary" + LINE_FEED + LINE_FEED).getBytes() );

        // Read file in chunks to avoid OOM
        byte[] buffer = new byte[4096];
        int bytesRead;
        try (InputStream inputStream = new FileInputStream(part.file)) {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        outputStream.write(LINE_FEED.getBytes());
    }

    private void addFileDataPart(ByteArrayOutputStream outputStream, Part part) throws IOException {
        outputStream.write( ("--" + BOUNDARY + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Disposition: form-data; name=\"" + part.name + "\"; filename=\"" + part.fileName + "\"" + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Type: " + part.type + LINE_FEED).getBytes() );
        outputStream.write( ("Content-Transfer-Encoding: binary" + LINE_FEED + LINE_FEED).getBytes() );
        
        outputStream.write(part.fileData);
        outputStream.write(LINE_FEED.getBytes());
    }

    @Override
    public String getBodyContentType() {
        return "multipart/form-data; boundary=" + BOUNDARY;
    }
}
