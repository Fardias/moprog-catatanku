package com.example.catatanku.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.ViewGroup.LayoutParams;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.example.catatanku.NoteDetailActivity;
import com.example.catatanku.R;
import com.example.catatanku.models.Note;

import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private Context context;
    private List<Note> noteList;

    public NoteAdapter(Context context, List<Note> noteList) {
        this.context = context;
        this.noteList = noteList;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = noteList.get(position);
        if (note == null) return;

        holder.titleTextView.setText(note.getTitle() != null ? note.getTitle() : "");
        
        // Truncate content if it's too long
        String content = note.getContent();
        if (content != null && content.length() > 40) {
            content = content.substring(0, 37) + "...";
        }
        holder.contentTextView.setText(content != null ? content : "");

        holder.imageView.setVisibility(View.GONE);

        holder.cardView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, NoteDetailActivity.class);
                intent.putExtra("note", note);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(context, "Gagal membuka catatan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView titleTextView, contentTextView;
        ImageView imageView;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            imageView = itemView.findViewById(R.id.noteImageView);
        }
    }
}
