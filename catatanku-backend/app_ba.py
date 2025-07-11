from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from werkzeug.utils import secure_filename
import mysql.connector
from mysql.connector import Error
import hashlib
import datetime
import os

app = Flask(__name__)
CORS(app)

# Konfigurasi
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '',
    'database': 'catatanku_db'
}

UPLOAD_FOLDER = 'Uploads'
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif'}

if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16 MB


# Utils
def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def hash_password(password):
    return hashlib.sha256(password.encode()).hexdigest()

def create_connection():
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        return conn
    except Error as e:
        print(f"DB connection error: {e}")
        return None

def close_connection(cursor, connection):
    if cursor:
        cursor.close()
    if connection:
        connection.close()

def format_datetime(dt):
    return dt.strftime('%d-%m-%Y %H:%M:%S') if isinstance(dt, datetime.datetime) else dt

# REGISTER
@app.route('/api/register', methods=['POST'])
def register():
    data = request.json
    name = data.get('name')
    email = data.get('email')
    password = data.get('password')

    if not all([name, email, password]):
        return jsonify({'success': False, 'message': 'Missing fields'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor()

    try:
        cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
        if cursor.fetchone():
            return jsonify({'success': False, 'message': 'Email already registered'}), 400

        hashed = hash_password(password)
        cursor.execute("INSERT INTO users (name, email, password) VALUES (%s, %s, %s)", (name, email, hashed))
        conn.commit()
        return jsonify({'success': True, 'message': 'Registered'}), 201
    except Error as e:
        print(f"Register error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)


@app.route('/api/login', methods=['POST'])
def login():
    data = request.json
    email = data.get('email')
    password = data.get('password')

    if not all([email, password]):
        return jsonify({'success': False, 'message': 'Missing fields'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        hashed = hash_password(password)
        cursor.execute("SELECT * FROM users WHERE email = %s AND password = %s", (email, hashed))
        user = cursor.fetchone()
        if user:
            user.pop('password', None)
            return jsonify({'success': True, 'user': user}), 200
        return jsonify({'success': False, 'message': 'Invalid credentials'}), 401
    except Error as e:
        print(f"Login error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)
        
        

@app.route('/api/profile', methods=['PUT'])
def update_profile():
    data = request.json
    user_id = data.get('user_id')
    new_name = data.get('name')
    new_email = data.get('email')


    if not all([user_id, new_name, new_email]):
        return jsonify({'success': False, 'message': 'ID Pengguna, Nama, dan Email wajib diisi'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'Koneksi database gagal'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        # 1. Ambil data pengguna saat ini untuk perbandingan
        cursor.execute("SELECT name, email FROM users WHERE id = %s", (user_id,))
        user = cursor.fetchone()
        if not user:
            return jsonify({'success': False, 'message': 'Pengguna tidak ditemukan'}), 404
        
        updates = []
        params = []
        
        # Cek dan tambahkan 'name' jika berubah
        if new_name and new_name != user['name']:
            updates.append("name = %s")
            params.append(new_name)

        # Cek dan tambahkan 'email' jika berubah
        if new_email and new_email != user['email']:
            # Pastikan email baru belum terdaftar oleh orang lain
            cursor.execute("SELECT id FROM users WHERE email = %s AND id != %s", (new_email, user_id))
            if cursor.fetchone():
                return jsonify({'success': False, 'message': 'Email ini sudah terdaftar oleh pengguna lain'}), 409
            updates.append("email = %s")
            params.append(new_email)
        
        # Jika tidak ada yang diubah sama sekali
        if not updates:
            # Tetap ambil data terbaru untuk konsistensi respons
            cursor.execute("SELECT id, name, email FROM users WHERE id = %s", (user_id,))
            current_user_data = cursor.fetchone()
            return jsonify({'success': True, 'message': 'Tidak ada perubahan yang disimpan', 'user': current_user_data}), 200

        # 2. Bangun dan eksekusi query UPDATE
        query = f"UPDATE users SET {', '.join(updates)} WHERE id = %s"
        params.append(user_id)
        
        cursor.execute(query, tuple(params))
        conn.commit()

        # 3. Ambil dan kirim kembali data pengguna yang sudah diperbarui
        cursor.execute("SELECT id, name, email FROM users WHERE id = %s", (user_id,))
        updated_user = cursor.fetchone()

        return jsonify({'success': True, 'message': 'Profil berhasil diperbarui', 'user': updated_user}), 200

    except Error as e:
        print(f"Update profile error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)
   

@app.route('/api/notes', methods=['GET'])
def get_notes():
    user_id = request.args.get('user_id')
    if not user_id:
        return jsonify({'success': False, 'message': 'User ID required'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT * FROM notes WHERE user_id = %s ORDER BY created_at DESC", (user_id,))
        notes = cursor.fetchall()
        for note in notes:
            note['created_at'] = format_datetime(note['created_at'])
        return jsonify({'success': True, 'data': notes}), 200
    except Error as e:
        print(f"Get notes error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)

@app.route('/api/notes', methods=['POST'])
def create_note():
    user_id = request.form.get('user_id')
    title = request.form.get('title')
    content = request.form.get('content', '')       
    image = request.files.get('image')
    
    # cek isi image
    if image:
        print(f"Received image: {image.filename}, size: {len(image.read())} bytes")
        image.seek(0)
    else:
        print("No image received")

    if not user_id or not title:
        return jsonify({'success': False, 'message': 'User ID and title required'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor()

    try:
        image_path = None
        if image and allowed_file(image.filename):
            try:
                filename = datetime.datetime.now().strftime('%Y%m%d_%H%M%S_') + secure_filename(image.filename)
                image_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
                image.save(image_path)
                # Simpan path relatif saja tanpa "Uploads/" di depannya
                db_path = filename
                print(f"Image saved successfully at: {image_path}")
            except Exception as e:
                print(f"Error saving image: {e}")
                # Log error details
                import traceback
                traceback.print_exc()
                db_path = None
        else:
            if image and not allowed_file(image.filename):
                print(f"Invalid file type: {image.filename}. Allowed types: {ALLOWED_EXTENSIONS}")
            db_path = None

        cursor.execute(
            "INSERT INTO notes (user_id, title, content, image_path) VALUES (%s, %s, %s, %s)",
            (user_id, title, content, db_path)
        )
        conn.commit()
        note_id = cursor.lastrowid
        # Ambil data note yang baru dibuat
        cursor.execute("SELECT * FROM notes WHERE id = %s", (note_id,))
        note = cursor.fetchone()
        if note:
            note_dict = {
                'id': note[0],
                'user_id': note[1],
                'title': note[2],
                'content': note[3],
                'image_path': note[4],
                'created_at': format_datetime(note[5])
            }
            return jsonify({'success': True, 'message': 'Note created', 'data': note_dict}), 201
        else:
            return jsonify({'success': True, 'message': 'Note created', 'data': None}), 201
    except Exception as e:
        print(f"Create note error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)

@app.route('/api/notes/<int:note_id>/image', methods=['GET'])
def get_note_image(note_id):
    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT image_path FROM notes WHERE id = %s", (note_id,))
        note = cursor.fetchone()
        if note and note['image_path']:
            # Path gambar di database sekarang hanya nama file
            filename = note['image_path']
            # Bangun path lengkap ke file gambar
            image_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
            print(f"Serving image from: {image_path}")
            
            if os.path.exists(image_path):
                try:
                    # Tentukan tipe MIME berdasarkan ekstensi file
                    mimetype = 'image/jpeg'  # default
                    if filename.lower().endswith('.png'):
                        mimetype = 'image/png'
                    elif filename.lower().endswith('.gif'):
                        mimetype = 'image/gif'
                    
                    print(f"Serving image with mimetype: {mimetype}")
                    return send_file(image_path, mimetype=mimetype)
                except Exception as e:
                    print(f"Error serving image: {e}")
                    import traceback
                    traceback.print_exc()
                    return jsonify({'success': False, 'message': f'Error serving image: {str(e)}'}), 500
            else:
                print(f"Image file not found at: {image_path}")
                return jsonify({'success': False, 'message': f'Image file not found at: {image_path}'}), 404
        print(f"No image path in database for note_id: {note_id}")
        return jsonify({'success': False, 'message': 'No image path in database'}), 404
    except Exception as e:
        print(f"Error serving image: {str(e)}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)

@app.route('/api/notes/<int:note_id>', methods=['PUT'])
def update_note(note_id):
    title = request.form.get('title')
    content = request.form.get('content', '')
    image = request.files.get('image')
    existing_image = request.form.get('existing_image')

    if not title:
        return jsonify({'success': False, 'message': 'Title required'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    # Gunakan dictionary=True agar bisa mengakses kolom dengan nama
    cursor = conn.cursor(dictionary=True)

    try:
        # Dapatkan path gambar lama untuk dihapus jika ada gambar baru
        cursor.execute("SELECT image_path FROM notes WHERE id = %s", (note_id,))
        old_note = cursor.fetchone()

        new_image_path = existing_image
        if image and allowed_file(image.filename):
            # Hapus gambar lama jika ada
            if old_note and old_note['image_path']:
                try:
                    old_path = os.path.join(app.config['UPLOAD_FOLDER'], old_note['image_path'])
                    if os.path.exists(old_path):
                        os.remove(old_path)
                except Exception as e:
                    print(f"Error deleting old image: {e}")

            # Simpan gambar baru
            try:
                filename = datetime.datetime.now().strftime('%Y%m%d_%H%M%S_') + secure_filename(image.filename)
                new_image_path = filename
                save_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
                image.save(save_path)
                print(f"New image saved successfully at: {save_path}")
            except Exception as e:
                print(f"Error saving new image: {e}")
                # Log error details
                import traceback
                traceback.print_exc()
                # Gunakan gambar yang ada jika gagal menyimpan gambar baru
                new_image_path = existing_image
                print(f"Keeping existing image: {existing_image}")
        
        # Lakukan UPDATE di database
        cursor.execute(
            "UPDATE notes SET title = %s, content = %s, image_path = %s WHERE id = %s",
            (title, content, new_image_path, note_id)
        )
        conn.commit()

        # --- PERUBAHAN UTAMA DIMULAI DI SINI ---
        # Setelah update, ambil data terbaru dari note tersebut
        cursor.execute("SELECT * FROM notes WHERE id = %s", (note_id,))
        updated_note = cursor.fetchone()

        if updated_note:
            # Format tanggal dan kirim kembali sebagai respons
            updated_note['created_at'] = format_datetime(updated_note['created_at'])
            return jsonify({'success': True, 'message': 'Note updated', 'data': updated_note}), 200
        else:
            # Fallback jika note tidak ditemukan setelah update (seharusnya tidak terjadi)
            return jsonify({'success': False, 'message': 'Failed to retrieve updated note'}), 404
        # --- PERUBAHAN UTAMA BERAKHIR DI SINI ---

    except Exception as e:
        print(f"Update error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)

@app.route('/api/notes/<int:note_id>', methods=['DELETE'])
def delete_note(note_id):
    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor()

    try:
        cursor.execute("SELECT image_path FROM notes WHERE id = %s", (note_id,))
        image_path = cursor.fetchone()
        if image_path and image_path[0]:
            try:
                full_path = os.path.join(app.config['UPLOAD_FOLDER'], image_path[0])
                if os.path.exists(full_path):
                    os.remove(full_path)
            except Exception as e:
                print(f"Delete image error: {e}")

        cursor.execute("DELETE FROM notes WHERE id = %s", (note_id,))
        conn.commit()
        return jsonify({'success': True, 'message': 'Note deleted'}), 200
    except Error as e:
        print(f"Delete note error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)

@app.route('/api/notes/<int:note_id>', methods=['GET'])
def get_note(note_id):
    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT * FROM notes WHERE id = %s", (note_id,))
        note = cursor.fetchone()
        if note:
            note['created_at'] = format_datetime(note['created_at'])
            return jsonify({'success': True, 'data': note}), 200
        return jsonify({'success': False, 'message': 'Note not found'}), 404
    finally:
        close_connection(cursor, conn)

# Run
if __name__ == '__main__':
    try:
        os.chmod(UPLOAD_FOLDER, 0o755)
    except Exception as e:
        print(f"Set permission error: {e}")
    app.run(host='0.0.0.0', port=5000, debug=True)
