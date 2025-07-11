from flask import Flask
from flask_cors import CORS

def create_app():
    app = Flask(__name__)
    
    from . import config
    app.config['UPLOAD_FOLDER'] = config.UPLOAD_FOLDER
    app.config['MAX_CONTENT_LENGTH'] = config.MAX_CONTENT_LENGTH
    
    CORS(app)

    from .routes.auth import auth_bp
    from .routes.profile import profile_bp
    from .routes.notes import notes_bp
    from .routes.password_reset import password_reset_bp
    
    app.register_blueprint(auth_bp)
    app.register_blueprint(profile_bp)
    app.register_blueprint(notes_bp)
    app.register_blueprint(password_reset_bp)

    
    @app.route('/health')
    def health_check():
        return "Server is up and running!"

    return app