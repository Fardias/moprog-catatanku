from flask import Blueprint, request, jsonify
from mysql.connector import Error
from ..utils.db import create_connection, close_connection
import os
from flask import send_file, current_app
from werkzeug.utils import secure_filename
from ..utils.helpers import allowed_file
import datetime

profile_bp = Blueprint('profile', __name__, url_prefix='/api')

@profile_bp.route('/profile', methods=['PUT'])
def update_profile():
    user_id = request.form.get('user_id')
    new_name = request.form.get('name')
    new_email = request.form.get('email')
    image_file = request.files.get('image')

    if not all([user_id, new_name, new_email]):
        return jsonify({'success': False, 'message': 'ID Pengguna, Nama, dan Email wajib diisi'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'Koneksi database gagal'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT name, email, profile_image_path FROM users WHERE id = %s", (user_id,))
        user = cursor.fetchone()
        if not user:
            return jsonify({'success': False, 'message': 'Pengguna tidak ditemukan'}), 404
        
        updates = []
        params = []
        
        if new_name and new_name != user['name']:
            updates.append("name = %s")
            params.append(new_name)

        if new_email and new_email != user['email']:
            cursor.execute("SELECT id FROM users WHERE email = %s AND id != %s", (new_email, user_id))
            if cursor.fetchone():
                return jsonify({'success': False, 'message': 'Email ini sudah terdaftar oleh pengguna lain'}), 409
            updates.append("email = %s")
            params.append(new_email)

        if image_file and allowed_file(image_file.filename):
            if user.get('profile_image_path'):
                try:
                    old_path = os.path.join(current_app.config['UPLOAD_FOLDER'], user['profile_image_path'])
                    if os.path.exists(old_path):
                        os.remove(old_path)
                except Exception as e:
                    print(f"Error deleting old profile image: {e}")

            filename = f"profile_{user_id}_{datetime.datetime.now().strftime('%Y%m%d%H%M%S')}_{secure_filename(image_file.filename)}"
            save_path = os.path.join(current_app.config['UPLOAD_FOLDER'], filename)
            image_file.save(save_path)
            
            updates.append("profile_image_path = %s")
            params.append(filename)

        if not updates:
            return jsonify({'success': True, 'message': 'Tidak ada perubahan yang disimpan', 'user': user}), 200

        query = f"UPDATE users SET {', '.join(updates)} WHERE id = %s"
        params.append(user_id)
        
        cursor.execute(query, tuple(params))
        conn.commit()

        cursor.execute("SELECT id, name, email, profile_image_path FROM users WHERE id = %s", (user_id,))
        updated_user = cursor.fetchone()

        return jsonify({'success': True, 'message': 'Profil berhasil diperbarui', 'user': updated_user}), 200

    except Error as e:
        print(f"Update profile error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)
        

@profile_bp.route('/profile/<int:user_id>/image', methods=['GET'])
def get_profile_image(user_id):
    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT profile_image_path FROM users WHERE id = %s", (user_id,))
        user = cursor.fetchone()
        if user and user.get('profile_image_path'):
            filename = user['profile_image_path']
            image_path = os.path.join(current_app.config['UPLOAD_FOLDER'], filename)

            if os.path.exists(image_path):
                return send_file(image_path)
            else:
                return jsonify({'success': False, 'message': 'Image file not found on server'}), 404
        
        return jsonify({'success': False, 'message': 'User has no profile image'}), 404
    except Error as e:
        print(f"Get profile image error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)