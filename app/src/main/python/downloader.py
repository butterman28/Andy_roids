import yt_dlp
import os
import json
import hashlib
#from supabase import create_client, Client

# --- Supabase setup ---
SUPABASE_URL = "https://jjwodouoxsovxggxxcmw.supabase.co"
SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impqd29kb3VveHNvdnhnZ3h4Y213Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAwNjUwNjAsImV4cCI6MjA3NTY0MTA2MH0.uWTP9Rm6xSf2AxtxQ3ojgu_991kxRTqBYMgeTan-Gr4"
# supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)
def upsert_download(file_hash, song_data):
    url = f"{SUPABASE_URL}/rest/v1/rpc/upsert_download"
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
    }
    payload = {"file_hash": file_hash, "metadata": song_data}

    response = requests.post(url, headers=headers, json=payload)

    # Debug info
    print(f"📡 Status: {response.status_code}")
    print("Response:", response.text)

    if response.ok:
        data = response.json()
        try:
            status = data.get("status")
            new_count = data.get("data", {}).get("downloads_count")
            print(f"✅ {status.upper()} — downloads_count now {new_count}")
        except Exception:
            print("ℹ️ RPC executed, but structure differs:", data)
        return data
    else:
        print("⚠️ RPC failed:", response.text)
        return None


# --- Helper: Compute hash ---
def compute_file_hash(filepath: str) -> str:
    """Compute SHA-256 hash of the given file."""
    sha = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            sha.update(chunk)
    return sha.hexdigest()


# --- Helper: Clean metadata ---
def extract_song_info(info: dict, file_hash: str):
    """Extracts concise human-readable metadata."""
    metadata = {}
    for key in [
        "fulltitle",
        "artists",
        "uploader",
        "album",
        "duration_string",
        "upload_date",
        "webpage_url",
        "release_date",
        "release_year",
        "creators",
        "description",
    ]:
        metadata[key] = info.get(key)
    metadata["hash"] = file_hash

    return metadata
def download_audio(url, output_dir):
    """
    Downloads the best audio and all metadata (JSON + cover art)
    without conversion or embedding — ideal for Chaquopy + Kotlin FFmpeg.
    Returns the full path of the downloaded file, or ERROR:: message if failed.
    """

    os.makedirs(output_dir, exist_ok=True)

    files_before = set(os.listdir(output_dir))

    ydl_opts = {
        # --- Download setup ---
        "format": "bestaudio[ext=m4a]/bestaudio/best",
        "outtmpl": os.path.join(output_dir, "%(artist, uploader)s - %(title)s.%(ext)s"),

        # --- Headers & network ---
        "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        "http_headers": {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Accept-Language": "en-US,en;q=0.9",
        },
        "retries": 10,
        "fragment_retries": 10,
        "source_address": "0.0.0.0",
        "ignoreerrors": True,

        # --- Metadata ---
        "writeinfojson": True,     # Save full metadata JSON
        "writethumbnail": True,    # Save thumbnail (cover art)
        "write_all_thumbnails": False,  # Only highest quality thumbnail
        "addmetadata": False,      # No embedding (we handle that later)

        # --- Silent but informative ---
        "quiet": False,
        "progress_hooks": [
            lambda d: print(f"Downloading: {d['filename']}") if d["status"] == "downloading" else None
        ],
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)

            # ✅ Determine downloaded filename
            downloaded_file = ydl.prepare_filename(info)

            # ✅ Look for the audio file
            if os.path.exists(downloaded_file):
                print(f"✅ Audio downloaded: {downloaded_file}")
            else:
                files_after = set(os.listdir(output_dir))
                new_files = files_after - files_before
                if new_files:
                    downloaded_file = os.path.join(output_dir, list(new_files)[0])
                else:
                    return f"ERROR::No new file created."

            # ✅ Find related files
            base, _ = os.path.splitext(downloaded_file)
            info_json = f"{base}.info.json"
            thumbnail = None

            # find any downloaded thumbnail (jpg/webp/etc)
            for ext in (".jpg", ".jpeg", ".png", ".webp"):
                possible_thumb = f"{base}{ext}"
                if os.path.exists(possible_thumb):
                    thumbnail = possible_thumb
                    break
            '''
            print(f"ℹ️ Metadata JSON: {info_json if os.path.exists(info_json) else 'Not found'}")
            print(f"🖼️ Thumbnail: {thumbnail if thumbnail else 'Not found'}")
            '''
            # --- Compute hash and check Supabase ---
            file_hash = compute_file_hash(downloaded_file)
            print(f"🔢 File hash: {file_hash[:16]}...")
            if os.path.exists(info_json):
                with open(info_json, "r", encoding="utf-8") as f:
                    meta = json.load(f)
                    song_data = extract_song_info(meta, file_hash)
                    # os.remove(info_json)
            """
            response = supabase.rpc(
                "upsert_download", {"file_hash": file_hash, "metadata": song_data}
            ).execute()
            """
            response = upsert_download(file_hash, song_data)
            # print(response)
            if response:
                print(f"✅ {status.upper()} — downloads_count now {new_count}")
                return json.dumps({
                    "audio": downloaded_file,
                    "info_json": song_data,
                    "thumbnail": thumbnail,
                    "hash": file_hash,
                })
            else:
                print("⚠️ RPC failed:", response)
    except Exception as e:
        return f"ERROR::{str(e)}"
