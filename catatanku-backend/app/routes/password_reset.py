import secrets
import datetime
from flask import Blueprint, request, jsonify
from mysql.connector import Error

from ..utils.db import create_connection, close_connection
from ..utils.helpers import hash_password

password_reset_bp = Blueprint('password_reset', __name__, url_prefix='/api/password-reset')


@password_reset_bp.route('/request', methods=['POST'])
def request_password_reset():
    data = request.json
    email = data.get('email')

    if not email:
        return jsonify({'success': False, 'message': 'Email is required'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'Database connection error'}), 500
    
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
        user = cursor.fetchone()

        if not user:
            return jsonify({'success': True, 'message': 'If an account with that email exists, a reset code has been sent.'}), 200

        user_id = user['id']

        cursor.execute("DELETE FROM password_resets WHERE user_id = %s", (user_id,))

        token = secrets.token_urlsafe(32)
        expires_at = datetime.datetime.utcnow() + datetime.timedelta(hours=1) # Token berlaku 1 jam
        cursor.execute(
            "INSERT INTO password_resets (user_id, token, expires_at) VALUES (%s, %s, %s)",
            (user_id, token, expires_at)
        )
        conn.commit()

        return jsonify({
            'success': True,
            'message': 'Password reset token generated.',
            'token': token 
        }), 200

    except Error as e:
        print(f"Request reset error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)


@password_reset_bp.route('/reset', methods=['POST'])
def perform_password_reset():
    data = request.json
    token = data.get('token')
    new_password = data.get('password')

    if not all([token, new_password]):
        return jsonify({'success': False, 'message': 'Token and new password are required'}), 400

    conn = create_connection()
    if not conn:
        return jsonify({'success': False, 'message': 'Database connection error'}), 500
    
    cursor = conn.cursor(dictionary=True)

    try:
        cursor.execute("SELECT * FROM password_resets WHERE token = %s", (token,))
        reset_request = cursor.fetchone()

        if not reset_request:
            return jsonify({'success': False, 'message': 'Invalid or expired token'}), 404

        if datetime.datetime.utcnow() > reset_request['expires_at']:
            cursor.execute("DELETE FROM password_resets WHERE token = %s", (token,))
            conn.commit()
            return jsonify({'success': False, 'message': 'Token has expired'}), 410 # 410 Gone

        user_id = reset_request['user_id']
        hashed_password = hash_password(new_password)

        cursor.execute(
            "UPDATE users SET password = %s WHERE id = %s",
            (hashed_password, user_id)
        )

        cursor.execute("DELETE FROM password_resets WHERE token = %s", (token,))
        
        conn.commit()

        return jsonify({'success': True, 'message': 'Password has been reset successfully'}), 200

    except Error as e:
        print(f"Perform reset error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)