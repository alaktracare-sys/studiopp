from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr
from passlib.context import CryptContext
from datetime import datetime, timedelta
import random
from database import get_db

router = APIRouter(tags=["auth"])

pwd_context = CryptContext(schemes=["bcrypt", "pbkdf2_sha256"], deprecated="auto")

# ---------------- MODELS ----------------

class SignupRequest(BaseModel):
    username: str
    email: EmailStr
    password: str
    confirm_password: str

class OTPVerifyRequest(BaseModel):
    email: EmailStr
    otp: str

class LoginRequest(BaseModel):
    identifier: str  # username OR email
    password: str


# ---------------- HELPERS ----------------

def hash_password(password: str) -> str:
    # Safely truncate to 72 bytes to adhere to bcrypt standard limits
    return pwd_context.hash(password[:72])

def verify_password(password: str, hashed: str) -> bool:
    try:
        return pwd_context.verify(password[:72], hashed)
    except Exception:
        return False

def generate_otp() -> str:
    return str(random.randint(100000, 999999))


# ---------------- SIGNUP ----------------

@router.post("/signup")
def signup(data: SignupRequest):
    if data.password != data.confirm_password:
        raise HTTPException(status_code=400, detail="Passwords do not match")

    if len(data.password) < 6:
        raise HTTPException(status_code=400, detail="Password must be at least 6 characters")

    conn = get_db()
    cursor = conn.cursor()

    # Check if username or email already exists
    cursor.execute("SELECT id FROM users WHERE username=? OR email=?", (data.username, data.email))
    existing = cursor.fetchone()
    if existing:
        conn.close()
        raise HTTPException(status_code=400, detail="User with this username or email already exists")

    otp = generate_otp()
    expiry = (datetime.utcnow() + timedelta(minutes=15)).isoformat()

    cursor.execute("""
        INSERT INTO users (username, email, password_hash, otp_code, otp_expiry)
        VALUES (?, ?, ?, ?, ?)
    """, (
        data.username,
        data.email,
        hash_password(data.password),
        otp,
        expiry
    ))

    conn.commit()
    conn.close()

    print(f"\n==========================================")
    print(f"🔑 [AUTH] Verification OTP for {data.email}: {otp}")
    print(f"==========================================\n")

    return {
        "message": f"OTP sent to {data.email} (Code: {otp})",
        "otp": otp
    }


# ---------------- VERIFY OTP ----------------

@router.post("/verify-otp")
def verify_otp(data: OTPVerifyRequest):
    conn = get_db()
    cursor = conn.cursor()

    cursor.execute("SELECT * FROM users WHERE email=?", (data.email,))
    user = cursor.fetchone()

    if not user:
        conn.close()
        raise HTTPException(status_code=404, detail="User not found")

    if user["otp_code"] != data.otp:
        conn.close()
        raise HTTPException(status_code=400, detail="Invalid OTP")

    if user["otp_expiry"]:
        try:
            expiry_dt = datetime.fromisoformat(user["otp_expiry"])
            if datetime.utcnow() > expiry_dt:
                conn.close()
                raise HTTPException(status_code=400, detail="OTP expired. Please request a new one.")
        except ValueError:
            pass

    cursor.execute("""
        UPDATE users
        SET is_verified=1, otp_code=NULL, otp_expiry=NULL
        WHERE email=?
    """, (data.email,))

    user_id = user["id"]

    # Ensure the system 'Liked Songs' playlist exists
    cursor.execute("""
        SELECT 1 FROM playlists
        WHERE user_id=? AND is_system=1
    """, (user_id,))
    exists = cursor.fetchone()

    if not exists:
        cursor.execute("""
            INSERT INTO playlists (user_id, name, is_system)
            VALUES (?, ?, 1)
        """, (user_id, "Liked Songs"))

    conn.commit()
    conn.close()

    return {"message": "Account verified successfully"}


# ---------------- LOGIN ----------------

@router.post("/login")
def login(data: LoginRequest):
    conn = get_db()
    cursor = conn.cursor()

    cursor.execute("""
        SELECT * FROM users
        WHERE username=? OR email=?
    """, (data.identifier, data.identifier))

    user = cursor.fetchone()

    if not user:
        conn.close()
        raise HTTPException(status_code=400, detail="Invalid credentials")

    if not verify_password(data.password, user["password_hash"]):
        conn.close()
        raise HTTPException(status_code=400, detail="Invalid credentials")

    if not user["is_verified"]:
        conn.close()
        raise HTTPException(status_code=400, detail="Account not verified. Please verify with OTP first.")

    # Ensure default system playlist exists
    user_id = user["id"]
    cursor.execute("""
        SELECT 1 FROM playlists
        WHERE user_id=? AND is_system=1
    """, (user_id,))
    if not cursor.fetchone():
        cursor.execute("""
            INSERT INTO playlists (user_id, name, is_system)
            VALUES (?, 'Liked Songs', 1)
        """, (user_id,))
        conn.commit()

    conn.close()

    return {
        "message": "Login successful",
        "user_id": user["id"],
        "username": user["username"],
        "email": user["email"],
    }
