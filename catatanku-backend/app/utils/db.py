import mysql.connector
from mysql.connector import Error
from ..config import DB_CONFIG

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