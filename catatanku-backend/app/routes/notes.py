import os
import datetime
import traceback
from flask import Blueprint, request, jsonify, send_file, current_app
from werkzeug.utils import secure_filename
from mysql.connector import Error

from ..utils.db import create_connection, close_connection
from ..utils.helpers import format_datetime, allowed_file

notes_bp = Blueprint('notes', __name__, url_prefix='/api/notes')

@notes_bp.route('', methods=['GET'])
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

@notes_bp.route('', methods=['POST'])
def create_note():
    user_id = request.form.get('user_id')
    title = request.form.get('title')
    content = request.form.get('content', '')
    image = request.files.get('image')

    if not user_id or not title:
        return jsonify({'success': False, 'message': 'User ID and title required'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True) 

    try:
        db_path = None
        if image and allowed_file(image.filename):
            try:
                filename = datetime.datetime.now().strftime('%Y%m%d_%H%M%S_') + secure_filename(image.filename)
                image_path = os.path.join(current_app.config['UPLOAD_FOLDER'], filename)
                image.save(image_path)
              
                db_path = filename
                print(f"Image saved to {image_path}")
            except Exception as e:
                print(f"Error saving image: {e}")
                traceback.print_exc()
                db_path = None

        cursor.execute(
            "INSERT INTO notes (user_id, title, content, image_path) VALUES (%s, %s, %s, %s)",
            (user_id, title, content, db_path)
        )
        conn.commit()
        note_id = cursor.lastrowid

        cursor.execute("SELECT * FROM notes WHERE id = %s", (note_id,))
        new_note = cursor.fetchone()
        if new_note:
            new_note['created_at'] = format_datetime(new_note['created_at'])
            return jsonify({'success': True, 'message': 'Note created', 'data': new_note}), 201
        
        return jsonify({'success': False, 'message': 'Note created but failed to retrieve'}), 201

    except Exception as e:
        print(f"Create note error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)

@notes_bp.route('/<int:note_id>/image', methods=['GET'])
def get_note_image(note_id):
    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT image_path FROM notes WHERE id = %s", (note_id,))
        note = cursor.fetchone()
        if note and note['image_path']:
            filename = note['image_path']

            image_path = os.path.join(current_app.config['UPLOAD_FOLDER'], filename)

            if os.path.exists(image_path):
                return send_file(image_path)
            else:
                return jsonify({'success': False, 'message': 'Image file not found on server'}), 404
        
        return jsonify({'success': False, 'message': 'No image associated with this note'}), 404
    except Exception as e:
        print(f"Error serving image: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)


@notes_bp.route('/<int:note_id>', methods=['PUT'])
def update_note(note_id):
    title = request.form.get('title')
    content = request.form.get('content', '')
    image = request.files.get('image')

    if not title:
        return jsonify({'success': False, 'message': 'Title required'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT image_path FROM notes WHERE id = %s", (note_id,))
        old_note = cursor.fetchone()
        if not old_note:
            return jsonify({'success': False, 'message': 'Note not found'}), 404

        new_image_path = old_note['image_path']
        if image and allowed_file(image.filename):
            # Hapus gambar lama jika ada
            if old_note['image_path']:
                try:
                    old_path = os.path.join(current_app.config['UPLOAD_FOLDER'], old_note['image_path'])
                    if os.path.exists(old_path):
                        os.remove(old_path)
                except Exception as e:
                    print(f"Error deleting old image: {e}")
            
            filename = datetime.datetime.now().strftime('%Y%m%d_%H%M%S_') + secure_filename(image.filename)
            save_path = os.path.join(current_app.config['UPLOAD_FOLDER'], filename)
            image.save(save_path)
            new_image_path = filename

        cursor.execute(
            "UPDATE notes SET title = %s, content = %s, image_path = %s WHERE id = %s",
            (title, content, new_image_path, note_id)
        )
        conn.commit()

        cursor.execute("SELECT * FROM notes WHERE id = %s", (note_id,))
        updated_note = cursor.fetchone()
        updated_note['created_at'] = format_datetime(updated_note['created_at'])
        
        return jsonify({'success': True, 'message': 'Note updated', 'data': updated_note}), 200

    except Exception as e:
        print(f"Update error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)

@notes_bp.route('/<int:note_id>', methods=['DELETE'])
def delete_note(note_id):
    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'DB error'}), 500
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT image_path FROM notes WHERE id = %s", (note_id,))
        note = cursor.fetchone()
        if note and note['image_path']:
            try:
                full_path = os.path.join(current_app.config['UPLOAD_FOLDER'], note['image_path'])
                if os.path.exists(full_path):
                    os.remove(full_path)
            except Exception as e:
                print(f"Delete image error: {e}")

        cursor.execute("DELETE FROM notes WHERE id = %s", (note_id,))
        conn.commit()

        if cursor.rowcount == 0:
            return jsonify({'success': False, 'message': 'Note not found'}), 404

        return jsonify({'success': True, 'message': 'Note deleted'}), 200
    except Error as e:
        print(f"Delete note error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)

@notes_bp.route('/<int:note_id>', methods=['GET'])
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
    except Error as e:
        print(f"Get single note error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)