import hashlib
import datetime
from ..config import ALLOWED_EXTENSIONS

def allowed_file(filename):
    # cek apakah ada nama file dan apakah ekstensi file diizinkan
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def hash_password(password):
    return hashlib.sha256(password.encode()).hexdigest()

def format_datetime(dt):
    if isinstance(dt, datetime.datetime):
        return dt.strftime('%d-%m-%Y %H:%M:%S')
    return dt