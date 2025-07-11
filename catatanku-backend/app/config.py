import os

DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '',
    'database': 'catatanku_db'
}

UPLOAD_FOLDER = os.path.join(os.path.abspath(os.path.dirname(__name__)), '..', 'Uploads')
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif'}
MAX_CONTENT_LENGTH = 16 * 1024 * 1024  # 16 MB

if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)
    try:
        os.chmod(UPLOAD_FOLDER, 0o755)
    except Exception as e:
        print(f"Set permission error on UPLOAD_FOLDER: {e}")