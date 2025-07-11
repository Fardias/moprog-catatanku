from flask import Blueprint, request, jsonify
from mysql.connector import Error
from ..utils.db import create_connection, close_connection
from ..utils.helpers import hash_password
import secrets

auth_bp = Blueprint('auth', __name__, url_prefix='/api')

@auth_bp.route('/register', methods=['POST'])
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

@auth_bp.route('/login', methods=['POST'])
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
            token = secrets.token_hex(24)
            user['token'] = token
            user.pop('password', None)
            return jsonify({'success': True, 'user': user}), 200
        return jsonify({'success': False, 'message': 'Invalid credentials'}), 401
    except Error as e:
        print(f"Login error: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500
    finally:
        close_connection(cursor, conn)