#!/usr/bin/env python3
"""
PageDrop Test Server
====================
A simple local HTTP server to test the Kindle-facing page on your actual Kindle.

Usage:
    1. Connect your Kindle and phone/laptop to the same WiFi network
       (or use your phone's hotspot and connect Kindle to it)
    2. Run: python3 test-server.py
    3. On your Kindle browser, go to: http://<your-ip>:8080
    4. Tap "SYNC" to test the download flow

    The server will:
    - Serve the Kindle UI page at /
    - Serve a sample test file at /download/<id>
    - Log all requests so you can see what the Kindle browser sends
"""

import http.server
import socketserver
import os
import sys
import socket
import json
from pathlib import Path
from urllib.parse import urlparse

PORT = 8080
KINDLE_UI_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "kindle-ui")

# Sample books data (simulates what the Android app would provide)
SAMPLE_BOOKS = [
    {
        "id": "1",
        "title": "The Great Gatsby",
        "author": "F. Scott Fitzgerald",
        "format": "AZW3",
        "size": "2.4 MB",
    },
    {
        "id": "2",
        "title": "1984",
        "author": "George Orwell",
        "format": "AZW3",
        "size": "1.8 MB",
    },
    {
        "id": "3",
        "title": "Pride and Prejudice",
        "author": "Jane Austen",
        "format": "PDF",
        "size": "3.1 MB",
    },
]


def generate_kindle_page(books):
    """Generate the Kindle-facing HTML page dynamically."""

    if not books:
        return generate_empty_page()

    book_cards = ""
    for book in books:
        book_cards += f"""
    <div class="book">
        <div class="book-title">{book['title']}</div>
        <div class="book-author">{book['author']}</div>
        <div class="book-meta">
            <span>{book['format']}</span>
            <span>&middot;</span>
            <span>{book['size']}</span>
        </div>
        <a href="/download/{book['id']}" class="sync-btn">&darr; SYNC</a>
    </div>
"""

    count = len(books)
    status_text = f"{count} book{'s' if count > 1 else ''} ready to sync"

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>PageDrop</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{
            font-family: Georgia, "Times New Roman", serif;
            background: #fff; color: #000;
            max-width: 580px; margin: 0 auto;
            padding: 20px 16px; line-height: 1.4;
            -webkit-text-size-adjust: 100%;
        }}
        .header {{
            text-align: center;
            padding-bottom: 16px;
            border-bottom: 2px solid #000;
            margin-bottom: 20px;
        }}
        .header h1 {{ font-size: 28px; letter-spacing: 1px; margin-bottom: 4px; }}
        .header .tagline {{ font-size: 14px; font-style: italic; color: #333; }}
        .status {{
            text-align: center; font-size: 15px;
            padding: 10px; margin-bottom: 20px;
            border: 1px solid #000; background: #f0f0f0;
        }}
        .book {{
            border: 2px solid #000;
            padding: 16px; margin-bottom: 16px;
        }}
        .book-title {{ font-size: 20px; font-weight: bold; margin-bottom: 4px; }}
        .book-author {{ font-size: 16px; color: #333; margin-bottom: 8px; }}
        .book-meta {{ font-size: 13px; color: #555; margin-bottom: 14px; }}
        .book-meta span {{ margin-right: 8px; }}
        .sync-btn {{
            display: block; text-align: center;
            background: #000; color: #fff;
            text-decoration: none;
            font-size: 20px; font-weight: bold;
            padding: 18px 0; letter-spacing: 1px;
        }}
        .sync-btn:visited {{ color: #fff; }}
        .sync-btn:active {{ background: #333; }}
        .footer {{
            text-align: center; font-size: 12px; color: #666;
            padding-top: 16px; border-top: 1px solid #999; margin-top: 20px;
        }}
    </style>
</head>
<body>
    <div class="header">
        <h1>PageDrop</h1>
        <div class="tagline">wireless book sync</div>
    </div>

    <div class="status">{status_text}</div>

    {book_cards}

    <div class="footer">PageDrop</div>
</body>
</html>"""


def generate_empty_page():
    """Generate the empty state page."""
    return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>PageDrop</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: Georgia, "Times New Roman", serif;
            background: #fff; color: #000;
            max-width: 580px; margin: 0 auto;
            padding: 20px 16px; line-height: 1.4;
        }
        .header {
            text-align: center;
            padding-bottom: 16px;
            border-bottom: 2px solid #000;
            margin-bottom: 20px;
        }
        .header h1 { font-size: 28px; letter-spacing: 1px; margin-bottom: 4px; }
        .header .tagline { font-size: 14px; font-style: italic; color: #333; }
        .empty {
            text-align: center; padding: 40px 16px;
        }
        .empty p { font-size: 18px; color: #555; margin-bottom: 10px; }
        .empty .hint { font-size: 14px; color: #888; font-style: italic; }
        .footer {
            text-align: center; font-size: 12px; color: #666;
            padding-top: 16px; border-top: 1px solid #999; margin-top: 20px;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>PageDrop</h1>
        <div class="tagline">wireless book sync</div>
    </div>
    <div class="empty">
        <p>No books queued</p>
        <div class="hint">Select a book on your phone to sync</div>
    </div>
    <div class="footer">PageDrop</div>
</body>
</html>"""


def generate_success_page(book):
    """Generate the success page after download."""
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>PageDrop</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{
            font-family: Georgia, "Times New Roman", serif;
            background: #fff; color: #000;
            max-width: 580px; margin: 0 auto;
            padding: 20px 16px; line-height: 1.4;
        }}
        .header {{
            text-align: center;
            padding-bottom: 16px;
            border-bottom: 2px solid #000;
            margin-bottom: 20px;
        }}
        .header h1 {{ font-size: 28px; letter-spacing: 1px; margin-bottom: 4px; }}
        .header .tagline {{ font-size: 14px; font-style: italic; color: #333; }}
        .success {{
            text-align: center; padding: 30px 16px;
            border: 2px solid #000; margin-bottom: 16px;
        }}
        .success .check {{ font-size: 48px; margin-bottom: 8px; }}
        .success p {{ font-size: 18px; }}
        .success .detail {{ font-size: 14px; color: #555; margin-top: 6px; }}
        .footer {{
            text-align: center; font-size: 12px; color: #666;
            padding-top: 16px; border-top: 1px solid #999; margin-top: 20px;
        }}
    </style>
</head>
<body>
    <div class="header">
        <h1>PageDrop</h1>
        <div class="tagline">wireless book sync</div>
    </div>
    <div class="success">
        <div class="check">&#10003;</div>
        <p>Synced!</p>
        <div class="detail">{book['title']} by {book['author']}</div>
    </div>
    <div class="footer">PageDrop</div>
</body>
</html>"""


class PageDropHandler(http.server.BaseHTTPRequestHandler):
    """Handle HTTP requests from the Kindle browser."""

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        # Log the request (useful for debugging Kindle browser behavior)
        print(f"\n{'='*50}")
        print(f"  Request: {self.command} {self.path}")
        print(f"  User-Agent: {self.headers.get('User-Agent', 'unknown')}")
        print(f"  From: {self.client_address[0]}")
        print(f"{'='*50}")

        if path == "/" or path == "":
            # Serve the main Kindle page
            self.serve_kindle_page()

        elif path.startswith("/download/"):
            # Handle book download
            book_id = path.split("/")[-1]
            self.serve_download(book_id)

        elif path == "/api/books":
            # JSON API for debugging
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(SAMPLE_BOOKS).encode())

        else:
            # Try to serve static files from kindle-ui/
            file_path = os.path.join(KINDLE_UI_DIR, path.lstrip("/"))
            if os.path.isfile(file_path):
                self.serve_static_file(file_path)
            else:
                self.send_error(404, "Not Found")

    def serve_kindle_page(self):
        """Serve the dynamically generated Kindle page."""
        html = generate_kindle_page(SAMPLE_BOOKS)
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(html.encode())))
        self.send_header("Cache-Control", "no-cache, no-store")
        self.end_headers()
        self.wfile.write(html.encode())
        print("  -> Served Kindle page")

    def serve_download(self, book_id):
        """Serve a book file for download."""
        book = next((b for b in SAMPLE_BOOKS if b["id"] == book_id), None)

        if not book:
            self.send_error(404, "Book not found")
            return

        # Check if there's an actual file to serve in kindle-ui/books/
        books_dir = os.path.join(KINDLE_UI_DIR, "books")
        potential_file = None
        if os.path.isdir(books_dir):
            for f in os.listdir(books_dir):
                if f.startswith(book_id + ".") or f.startswith(book_id + "_"):
                    potential_file = os.path.join(books_dir, f)
                    break

        if potential_file and os.path.isfile(potential_file):
            # Serve the actual file
            filename = os.path.basename(potential_file)
            file_size = os.path.getsize(potential_file)

            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
            self.send_header("Content-Length", str(file_size))
            self.end_headers()

            with open(potential_file, "rb") as f:
                self.wfile.write(f.read())

            print(f"  -> Served file: {filename} ({file_size} bytes)")
        else:
            # No actual file — serve success page instead (for UI testing)
            html = generate_success_page(book)
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html.encode())))
            self.end_headers()
            self.wfile.write(html.encode())
            print(f"  -> No file found for book {book_id}, showed success page")
            print(f"     (Place files in kindle-ui/books/ to test real downloads)")

    def serve_static_file(self, file_path):
        """Serve a static file."""
        ext = os.path.splitext(file_path)[1].lower()
        mime_types = {
            ".html": "text/html",
            ".css": "text/css",
            ".js": "application/javascript",
            ".png": "image/png",
            ".jpg": "image/jpeg",
            ".ico": "image/x-icon",
        }
        mime_type = mime_types.get(ext, "application/octet-stream")

        with open(file_path, "rb") as f:
            content = f.read()

        self.send_response(200)
        self.send_header("Content-Type", mime_type)
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def log_message(self, format, *args):
        """Suppress default logging (we do our own)."""
        pass


def get_local_ip():
    """Get the machine's local IP address."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def main():
    local_ip = get_local_ip()

    print()
    print("=" * 56)
    print("  PageDrop Test Server")
    print("=" * 56)
    print()
    print(f"  Local URL:   http://localhost:{PORT}")
    print(f"  Network URL: http://{local_ip}:{PORT}")
    print()
    print("  ┌──────────────────────────────────────┐")
    print(f"  │  On your Kindle browser, go to:      │")
    url = f"http://{local_ip}:{PORT}"
    print(f"  │  {url:<36s}│")
    print("  └──────────────────────────────────────┘")
    print()
    print("  To test real file downloads:")
    print(f"  Place .azw3/.mobi/.pdf files in:")
    print(f"  kindle-ui/books/1.azw3, 2.azw3, etc.")
    print()
    print("  Press Ctrl+C to stop")
    print("=" * 56)
    print()

    with socketserver.TCPServer(("", PORT), PageDropHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n\nServer stopped.")
            sys.exit(0)


if __name__ == "__main__":
    main()
