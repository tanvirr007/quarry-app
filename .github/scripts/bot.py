import os
import sys
import urllib.request
import urllib.parse
import json
import uuid
import subprocess
import time
import hashlib
import re
import threading


# ═══════════════════════════════════════════════════════════════════
# Telegram API
# ═══════════════════════════════════════════════════════════════════

def escape_html(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

def format_changelog(changelog, repo):
    formatted_lines = []
    for line in changelog.splitlines():
        is_indented = line.startswith('  ') or line.startswith('\t')
        stripped = line.strip()
        if not stripped:
            continue

        # Match format: * **Title** (hash)
        m = re.match(r'^[*•-]\s*\*\*(.+)\*\*\s*\(([^)]+)\)$', stripped)
        if m:
            title = m.group(1)
            short_hash = m.group(2).strip('()')
            commit_url = f"https://github.com/{repo}/commit/{short_hash}" if repo else "#"
            formatted_line = f"• <b>{escape_html(title)}</b> (<a href=\"{commit_url}\">{short_hash}</a>)"
            formatted_lines.append(formatted_line)
        elif not is_indented:
            clean_text = re.sub(r'^[-*•]+\s*', '', stripped)
            formatted_lines.append(f"• <b>{escape_html(clean_text)}</b>")
        else:
            clean_text = re.sub(r'^[-*•]+\s*', '', stripped)
            formatted_lines.append(f"  - {escape_html(clean_text)}")
    return "\n".join(formatted_lines)

def send_request(req):
    try:
        with urllib.request.urlopen(req) as response:
            return response.read()
    except Exception as e:
        if hasattr(e, 'read'):
            try:
                error_body = e.read().decode('utf-8')
                print(f"HTTP Error details: {error_body}")
            except Exception:
                pass
        raise e

def send_photo(token, chat_id, filepath, caption, reply_markup):
    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}

    body = []
    body.append(f"--{boundary}".encode('utf-8'))
    body.append(b'Content-Disposition: form-data; name="chat_id"')
    body.append(b'')
    body.append(str(chat_id).encode('utf-8'))

    body.append(f"--{boundary}".encode('utf-8'))
    body.append(b'Content-Disposition: form-data; name="parse_mode"')
    body.append(b'')
    body.append(b'HTML')

    body.append(f"--{boundary}".encode('utf-8'))
    body.append(b'Content-Disposition: form-data; name="reply_markup"')
    body.append(b'')
    body.append(json.dumps(reply_markup).encode('utf-8'))

    body.append(f"--{boundary}".encode('utf-8'))
    body.append(b'Content-Disposition: form-data; name="caption"')
    body.append(b'')
    body.append(caption.encode('utf-8'))

    filename = os.path.basename(filepath)
    body.append(f"--{boundary}".encode('utf-8'))
    body.append(f'Content-Disposition: form-data; name="photo"; filename="{filename}"'.encode('utf-8'))
    body.append(b'Content-Type: image/png')
    body.append(b'')
    with open(filepath, 'rb') as f:
        body.append(f.read())

    body.append(f"--{boundary}--".encode('utf-8'))
    body.append(b'')

    payload = b'\r\n'.join(body)
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendPhoto",
        data=payload,
        headers=headers
    )
    return send_request(req)

def send_message(token, chat_id, message, reply_markup=None):
    data = {
        "chat_id": chat_id,
        "text": message,
        "parse_mode": "HTML",
        "disable_web_page_preview": True
    }
    if reply_markup is not None:
        data["reply_markup"] = reply_markup
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendMessage",
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    return send_request(req)

def edit_message(token, chat_id, message_id, text, reply_markup=None):
    data = {
        "chat_id": chat_id,
        "message_id": message_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": True
    }
    if reply_markup is not None:
        data["reply_markup"] = reply_markup
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/editMessageText",
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    return send_request(req)

def send_document(token, chat_id, filepath, caption=None):
    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}

    body = []
    body.append(f"--{boundary}".encode('utf-8'))
    body.append(b'Content-Disposition: form-data; name="chat_id"')
    body.append(b'')
    body.append(str(chat_id).encode('utf-8'))

    if caption:
        body.append(f"--{boundary}".encode('utf-8'))
        body.append(b'Content-Disposition: form-data; name="caption"')
        body.append(b'')
        body.append(caption.encode('utf-8'))

        body.append(f"--{boundary}".encode('utf-8'))
        body.append(b'Content-Disposition: form-data; name="parse_mode"')
        body.append(b'')
        body.append(b'HTML')

    filename = os.path.basename(filepath)
    body.append(f"--{boundary}".encode('utf-8'))
    body.append(f'Content-Disposition: form-data; name="document"; filename="{filename}"'.encode('utf-8'))
    body.append(b'Content-Type: application/octet-stream')
    body.append(b'')
    with open(filepath, 'rb') as f:
        body.append(f.read())

    body.append(f"--{boundary}--".encode('utf-8'))
    body.append(b'')

    payload = b'\r\n'.join(body)
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendDocument",
        data=payload,
        headers=headers
    )
    return send_request(req)

def send_media_group(token, chat_id, filepaths):
    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}

    body = []
    body.append(f"--{boundary}".encode('utf-8'))
    body.append(b'Content-Disposition: form-data; name="chat_id"')
    body.append(b'')
    body.append(str(chat_id).encode('utf-8'))

    media_list = []
    for i, filepath in enumerate(filepaths):
        attach_name = f"doc_{i}"
        media_list.append({
            "type": "document",
            "media": f"attach://{attach_name}"
        })

    body.append(f"--{boundary}".encode('utf-8'))
    body.append(b'Content-Disposition: form-data; name="media"')
    body.append(b'')
    body.append(json.dumps(media_list).encode('utf-8'))

    for i, filepath in enumerate(filepaths):
        attach_name = f"doc_{i}"
        filename = os.path.basename(filepath)
        body.append(f"--{boundary}".encode('utf-8'))
        body.append(f'Content-Disposition: form-data; name="{attach_name}"; filename="{filename}"'.encode('utf-8'))
        body.append(b'Content-Type: application/octet-stream')
        body.append(b'')
        with open(filepath, 'rb') as f:
            body.append(f.read())

    body.append(f"--{boundary}--".encode('utf-8'))
    body.append(b'')

    payload = b'\r\n'.join(body)
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendMediaGroup",
        data=payload,
        headers=headers
    )
    return send_request(req)


# ═══════════════════════════════════════════════════════════════════
# Monitor — Live build progress via Telegram
# ═══════════════════════════════════════════════════════════════════

def format_time(seconds):
    """Format seconds as XXm:XXs or XXh:XXm:XXs."""
    h = int(seconds) // 3600
    m = (int(seconds) % 3600) // 60
    s = int(seconds) % 60
    if h > 0:
        return f"{h:02d}h:{m:02d}m:{s:02d}s"
    return f"{m:02d}m:{s:02d}s"

def format_duration_text(seconds):
    """Format seconds as human-readable text like '2 minutes and 12 seconds'."""
    h = int(seconds) // 3600
    m = (int(seconds) % 3600) // 60
    s = int(seconds) % 60
    if h > 0:
        return f"{h} {'hour' if h == 1 else 'hours'} and {m} {'minute' if m == 1 else 'minutes'}"
    if m > 0:
        return f"{m} {'minute' if m == 1 else 'minutes'} and {s} {'second' if s == 1 else 'seconds'}"
    return f"{s} {'second' if s == 1 else 'seconds'}"

def format_size(size_bytes):
    """Format file size to human-readable."""
    if size_bytes < 1024:
        return f"{size_bytes} B"
    if size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    if size_bytes < 1024 ** 3:
        return f"{size_bytes / (1024 * 1024):.1f} MB"
    return f"{size_bytes / (1024 ** 3):.2f} GB"

def count_dry_run_tasks():
    """Run Gradle --dry-run to auto-detect total task count."""
    try:
        result = subprocess.run(
            ['./gradlew', 'assembleRelease', '--dry-run'],
            capture_output=True, text=True, timeout=120
        )
        count = sum(1 for line in result.stdout.splitlines()
                    if line.strip().startswith('> Task '))
        if count == 0:
            count = sum(1 for line in result.stdout.splitlines()
                        if line.strip().startswith(':') and 'SKIPPED' in line)
        return count if count > 0 else 50
    except Exception as e:
        print(f"Warning: dry-run failed ({e}), using estimate of 50 tasks")
        return 50

def monitor():
    """Live build monitor with Telegram progress updates."""
    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID")
    version = os.environ.get("VERSION", "unknown")
    app_name = os.environ.get("APP_NAME", "Quarry")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    server_url = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    ref_name = os.environ.get("GITHUB_REF_NAME", "unknown")

    # Get commit hash
    try:
        commit_hash = subprocess.check_output(
            ['git', 'rev-parse', '--short', 'HEAD'], text=True
        ).strip()
    except Exception:
        commit_hash = "unknown"

    action_url = f"{server_url}/{repo}/actions/runs/{run_id}"
    release_url = f"https://github.com/{repo}/releases/tag/v{version}"
    telegram_ok = bool(token and chat_id)
    message_id = None

    # ── Phase 1: Dry-run to count tasks ──
    print("Analyzing build tasks...")
    total_tasks = count_dry_run_tasks()
    print(f"Detected {total_tasks} tasks")

    # Helper variables for tracking progress
    completed = 0
    current_task = "Setup JDK 21"
    failed_task = None
    start_time = time.time()
    lock = threading.Lock()
    running = True

    def make_progress_msg():
        with lock:
            c, ct = completed, current_task
        elapsed = time.time() - start_time
        pct = min(int(c / total_tasks * 100), 99) if total_tasks > 0 else 0
        text = (
            f"<b>Building APK</b>\n\n"
            f"• APP: <code>{escape_html(app_name)}</code>\n"
            f"• VERSION: <code>v{escape_html(version)}</code>\n"
            f"• BRANCH: <code>{escape_html(ref_name)}</code>\n"
            f"• PROGRESS: <code>{pct}% ({c}/{total_tasks})</code>\n"
            f"<blockquote>{escape_html(ct)}</blockquote>\n"
            f"• ELAPSED TIME: <code>{format_time(elapsed)}</code>\n"
        )
        return text

    # ── Phase 2: Send initial message and pre-build setup logs ──
    if telegram_ok:
        try:
            resp = send_message(token, chat_id, make_progress_msg())
            data = json.loads(resp)
            message_id = data['result']['message_id']
            print(f"Telegram: Initial message sent (id: {message_id})")
        except Exception as e:
            print(f"Warning: Failed to send initial message: {e}")
            telegram_ok = False

        if telegram_ok and message_id:
            time.sleep(1.5)
            with lock:
                current_task = "Write sign info"
            try:
                edit_message(token, chat_id, message_id, make_progress_msg())
            except Exception:
                pass

            time.sleep(1.5)
            with lock:
                current_task = "Set version name"
            try:
                edit_message(token, chat_id, message_id, make_progress_msg())
            except Exception:
                pass

            with lock:
                current_task = "starting..."

    # ── Phase 3: Build with live progress ──
    def update_loop():
        last_text = ""
        while running:
            time.sleep(3)
            if not running:
                break
            if telegram_ok and message_id:
                try:
                    text = make_progress_msg()
                    if text != last_text:
                        edit_message(token, chat_id, message_id, text)
                        last_text = text
                except Exception:
                    pass

    thread = threading.Thread(target=update_loop, daemon=True)
    thread.start()

    log_file = open('build_log.txt', 'w')
    task_re = re.compile(r'^> Task (\S+)')
    fail_re = re.compile(r'^> Task (\S+)\s+FAILED')

    process = subprocess.Popen(
        ['./gradlew', 'assembleRelease'],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1
    )

    try:
        for line in iter(process.stdout.readline, ''):
            sys.stdout.write(line)
            sys.stdout.flush()
            log_file.write(line)

            stripped = line.strip()
            fm = fail_re.match(stripped)
            if fm:
                with lock:
                    failed_task = fm.group(1)
                    completed += 1
                    current_task = fm.group(1)
            else:
                tm = task_re.match(stripped)
                if tm:
                    with lock:
                        completed += 1
                        current_task = tm.group(1)
    finally:
        process.wait()
        exit_code = process.returncode
        log_file.close()
        running = False
        thread.join(timeout=5)

    elapsed = time.time() - start_time

    # ── Phase 4: Final message ──
    if telegram_ok and message_id:
        if exit_code == 0:
            text = (
                f"<b>APK compiled!</b>\n\n"
                f"• APP: <code>{escape_html(app_name)}</code>\n"
                f"• VERSION: <code>v{escape_html(version)}</code>\n"
                f"• BUILD TIME: <code>{format_time(elapsed)}</code>\n"
                f"• TASKS: <code>{completed} executed</code>\n\n"
                f"<i>Compilation took {format_duration_text(elapsed)}</i>"
            )
            markup = {"inline_keyboard": [[
                {"text": "Action", "url": action_url},
                {"text": "Releases", "url": release_url}
            ]]}
            try:
                edit_message(token, chat_id, message_id, text, markup)
                print("Telegram: Success message sent")
            except Exception as e:
                print(f"Warning: Failed to edit success message: {e}")
        else:
            ft = failed_task or current_task or "unknown"
            text = (
                f"<b>Build failed!</b>\n\n"
                f"• APP: <code>{escape_html(app_name)}</code>\n"
                f"• VERSION: <code>v{escape_html(version)}</code>\n"
                f"• FAILED AT: <code>{escape_html(ft)}</code>\n"
                f"• ELAPSED TIME: <code>{format_time(elapsed)}</code>\n"
                f"• TASKS: <code>{completed}/{total_tasks} completed</code>"
            )
            markup = {"inline_keyboard": [[
                {"text": "Action", "url": action_url}
            ]]}
            try:
                edit_message(token, chat_id, message_id, text, markup)
                print("Telegram: Failure message sent")
            except Exception as e:
                print(f"Warning: Failed to edit failure message: {e}")

            if os.path.exists('build_log.txt'):
                try:
                    send_document(token, chat_id, 'build_log.txt',
                                  caption=f"Build log for v{version}")
                    print("Telegram: Build log sent")
                except Exception as e:
                    print(f"Warning: Failed to send build log: {e}")

    sys.exit(exit_code)


# ═══════════════════════════════════════════════════════════════════
# Release — Post-build notification
# ═══════════════════════════════════════════════════════════════════

def send_changelog_in_chunks(token, chat_id, header, changelog_lines):
    """Sends a changelog to Telegram, chunking it if it exceeds the limit."""
    limit = 4000
    if not changelog_lines:
        if header:
            send_message(token, chat_id, header, None)
        return

    current_chunk = []
    if header:
        current_chunk.append(header)

    current_length = sum(len(c) for c in current_chunk) + len(current_chunk) - 1 if current_chunk else 0

    part_index = 1
    for line in changelog_lines:
        line_len = len(line)
        if line_len > limit - 100:
            line = line[:limit - 100] + "..."
            line_len = len(line)

        extra_len = 1 if current_chunk else 0
        if current_length + line_len + extra_len > limit:
            if current_chunk:
                send_message(token, chat_id, "\n".join(current_chunk), None)
            
            part_index += 1
            continuation_header = f"📝 <b>Changelog (continued - Part {part_index}):</b>"
            current_chunk = [continuation_header, line]
            current_length = len(continuation_header) + 1 + line_len
        else:
            current_chunk.append(line)
            current_length += line_len + extra_len

    if current_chunk:
        send_message(token, chat_id, "\n".join(current_chunk), None)


def release():
    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID")
    version = os.environ.get("VERSION", "unknown")
    commit_hash = os.environ.get("COMMIT_HASH", "")
    build_time = os.environ.get("BUILD_TIME", "")
    apk_sha = os.environ.get("APK_SHA", "")
    changelog = os.environ.get("CHANGELOG", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    apk_versioned_path = os.environ.get("APK_VERSIONED_PATH", "")
    apk_latest_path = os.environ.get("APK_LATEST_PATH", "")

    if not token or not chat_id:
        print("TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is missing")
        sys.exit(1)

    changelog_html = format_changelog(changelog, repo)
    short_commit = commit_hash[:7] if commit_hash else "unknown"
    commit_link = f'<a href="https://github.com/{repo}/commit/{commit_hash}">{short_commit}</a>' if (repo and commit_hash) else short_commit

    message = (
        f"<b>New update available (v{version})</b>\n\n"
        f"📦 <b>Build Information:</b>\n"
        f"- <b>Version:</b> <code>v{version}</code>\n"
        f"- <b>Commit:</b> {commit_link}\n"
        f"- <b>Build Time:</b> <code>{build_time}</code>\n"
        f"- <b>Android:</b> <code>12.0+</code>\n"
        f"- <b>SHA-256:</b> <code>{apk_sha}</code>\n\n"
        f"📝 <b>Changelog:</b>\n"
        f"{changelog_html}"
    )

    reply_markup = {
        "inline_keyboard": [
            [
                {"text": "Direct", "url": f"https://github.com/{repo}/releases/latest/download/Quarry.apk"},
                {"text": "Versioned", "url": f"https://github.com/{repo}/releases/download/v{version}/Quarry-v{version}.apk"}
            ]
        ]
    }

    # Upload photo using multipart/form-data
    banner_path = "assets/update.png"
    if os.path.exists(banner_path):
        photo_caption = message
        send_separate_changelog = False

        if len(message) > 1024:
            photo_caption = (
                f"<b>New update available (v{version})</b>\n\n"
                f"📦 <b>Build Information:</b>\n"
                f"- <b>Version:</b> <code>v{version}</code>\n"
                f"- <b>Commit:</b> {commit_link}\n"
                f"- <b>Build Time:</b> <code>{build_time}</code>\n"
                f"- <b>Android:</b> <code>12.0+</code>\n"
                f"- <b>SHA-256:</b> <code>{apk_sha}</code>\n\n"
                f"📝 <i>Changelog is too long and is sent below.</i>"
            )
            send_separate_changelog = True

        print(f"Uploading banner: {banner_path} with message caption...")
        photo_sent = False
        try:
            send_photo(token, chat_id, banner_path, photo_caption, reply_markup)
            print("Successfully sent photo post.")
            photo_sent = True
        except Exception as e:
            print(f"Error sending photo: {e}")

        if photo_sent:
            if send_separate_changelog:
                try:
                    changelog_header = f"📝 <b>Changelog for v{version}:</b>"
                    changelog_lines = changelog_html.splitlines()
                    send_changelog_in_chunks(token, chat_id, changelog_header, changelog_lines)
                    print("Successfully sent separate changelog message(s).")
                except Exception as cle:
                    print(f"Error sending separate changelog: {cle}")
        else:
            try:
                main_info = (
                    f"<b>New update available (v{version})</b>\n\n"
                    f"📦 <b>Build Information:</b>\n"
                    f"- <b>Version:</b> <code>v{version}</code>\n"
                    f"- <b>Commit:</b> {commit_link}\n"
                    f"- <b>Build Time:</b> <code>{build_time}</code>\n"
                    f"- <b>Android:</b> <code>12.0+</code>\n"
                    f"- <b>SHA-256:</b> <code>{apk_sha}</code>\n\n"
                    f"📝 <b>Changelog:</b>"
                )
                send_message(token, chat_id, main_info, reply_markup)
                changelog_lines = changelog_html.splitlines()
                send_changelog_in_chunks(token, chat_id, "", changelog_lines)
                print("Successfully sent fallback text messages.")
            except Exception as fe:
                print(f"Error sending fallback text: {fe}")
    else:
        print("assets/update.png not found, sending as text post...")
        try:
            main_info = (
                f"<b>New update available (v{version})</b>\n\n"
                f"📦 <b>Build Information:</b>\n"
                f"- <b>Version:</b> <code>v{version}</code>\n"
                f"- <b>Commit:</b> {commit_link}\n"
                f"- <b>Build Time:</b> <code>{build_time}</code>\n"
                f"- <b>Android:</b> <code>12.0+</code>\n"
                f"- <b>SHA-256:</b> <code>{apk_sha}</code>\n\n"
                f"📝 <b>Changelog:</b>"
            )
            send_message(token, chat_id, main_info, reply_markup)
            changelog_lines = changelog_html.splitlines()
            send_changelog_in_chunks(token, chat_id, "", changelog_lines)
            print("Successfully sent text post.")
        except Exception as e:
            print(f"Error sending text post: {e}")

    # Collect existing APKs
    apks_to_upload = []
    if apk_latest_path and os.path.exists(apk_latest_path):
        apks_to_upload.append(apk_latest_path)
    else:
        print(f"Latest APK not found at {apk_latest_path}")

    if apk_versioned_path and os.path.exists(apk_versioned_path):
        apks_to_upload.append(apk_versioned_path)
    else:
        print(f"Versioned APK not found at {apk_versioned_path}")

    if apks_to_upload:
        print(f"Uploading {len(apks_to_upload)} APK(s)...")
        try:
            send_media_group(token, chat_id, apks_to_upload)
            print("Successfully sent combined media group.")
        except Exception as e:
            print(f"Error sending media group ({e}), falling back to individual document uploads...")
            for apk_file in apks_to_upload:
                try:
                    send_document(token, chat_id, apk_file, caption=f"<code>{os.path.basename(apk_file)}</code>")
                    print(f"Successfully uploaded {apk_file} individually.")
                except Exception as doc_err:
                    print(f"Failed to upload {apk_file}: {doc_err}")


# ═══════════════════════════════════════════════════════════════════
# Changelog — Regenerate changelog.json from GitHub releases
# ═══════════════════════════════════════════════════════════════════

def fetch_releases(repo, token):
    """Fetch all releases (newest first) from the GitHub API."""
    releases = []
    page = 1
    while True:
        url = f"https://api.github.com/repos/{repo}/releases?per_page=100&page={page}"
        req = urllib.request.Request(url, headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "Quarry-Changelog"
        })
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        with urllib.request.urlopen(req, timeout=30) as resp:
            batch = json.loads(resp.read().decode("utf-8"))
        if not batch:
            break
        releases.extend(batch)
        page += 1
        if len(batch) < 100:
            break
    return releases

def parse_changelog_section(body):
    if not body or "### 📝 Changelog" not in body:
        return []
    section = body.split("### 📝 Changelog", 1)[1]

    main_re = re.compile(r'^\s*[*•-]\s*(.+?)\s*$')
    hash_re = re.compile(r'\s*\([0-9a-f]{7,8}\)\s*$')
    sub_re = re.compile(r'^[-*•]+\s*(.*)$')
    test_re = re.compile(r'^[-*•]?\s*TEST\s*:?\s*$', re.IGNORECASE)

    items = []
    current = None
    skipping_test = False
    for raw in section.splitlines():
        stripped = raw.strip()
        if not stripped:
            continue
        is_indented = raw.startswith(" ") or raw.startswith("\t")
        if not is_indented:
            skipping_test = False

        m = main_re.match(stripped)
        if m and not is_indented:
            title = hash_re.sub('', m.group(1)).strip()
            title = title.strip('*').strip()
            if title:
                current = {"title": title, "subItems": []}
                items.append(current)
            skipping_test = False
            continue

        if skipping_test:
            continue
        if test_re.match(stripped):
            skipping_test = True
            continue

        sm = sub_re.match(stripped)
        if sm and current is not None:
            text = sm.group(1).strip()
            if text:
                current["subItems"].append(text)
    return items

def changelog():
    repo = os.environ.get("REPOSITORY", "tanvirr007/quarry-app")
    token = os.environ.get("GH_TOKEN", "")
    expected_tag = os.environ.get("VERSION_NAME", "").strip()
    if expected_tag and not expected_tag.startswith("v"):
        expected_tag = f"v{expected_tag}"

    releases = []
    attempts = 0
    while attempts < 3:
        try:
            releases = fetch_releases(repo, token)
        except Exception as e:
            print(f"Warning: failed to fetch releases ({e})")
            releases = []
        if expected_tag and not any(r.get("tag_name") == expected_tag for r in releases):
            attempts += 1
            print(f"Warning: {expected_tag} not found yet (attempt {attempts}/3), retrying...")
            time.sleep(2)
            continue
        break

    if expected_tag and not any(r.get("tag_name") == expected_tag for r in releases):
        print(f"WARNING: {expected_tag} missing from changelog.json — will catch up on the next release")

    out = {"releases": []}
    for r in releases:
        if r.get("draft"):
            continue
        items = parse_changelog_section(r.get("body"))
        if not items:
            continue
        out["releases"].append({
            "tagName": r.get("tag_name", ""),
            "publishedAt": r.get("published_at", ""),
            "items": items
        })

    out_path = "changelog.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)

    print(f"Successfully generated {out_path} with {len(out['releases'])} release(s)")


def ota():
    run_number_str = os.environ.get("RUN_NUMBER", "1")
    version_name = os.environ.get("VERSION_NAME", "1.0.0")
    repo = os.environ.get("REPOSITORY", "tanvirr007/quarry-app")
    changelog = os.environ.get("CHANGELOG", "No changelog provided.")

    try:
        version_code = int(run_number_str)
    except ValueError:
        version_code = 1

    full_version_name = f"v{version_name}"
    download_url = f"https://github.com/{repo}/releases/download/{full_version_name}/Quarry.apk"

    file_size = None
    for candidate in (
        f"app/build/outputs/apk/release/Quarry-v{version_name}.apk",
        "app/build/outputs/apk/release/Quarry.apk",
        "app/build/outputs/apk/release/app-release.apk",
    ):
        try:
            file_size = os.path.getsize(candidate)
            break
        except OSError:
            continue

    manifest = {
        "versionCode": version_code,
        "versionName": full_version_name,
        "downloadUrl": download_url,
        "changelog": changelog
    }
    if file_size:
        manifest["fileSize"] = file_size

    out_path = "version.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print(f"Successfully generated {out_path} for {full_version_name}" + (f" ({file_size} bytes)" if file_size else " (no APK size)"))


# ═══════════════════════════════════════════════════════════════════
# Entry Point
# ═══════════════════════════════════════════════════════════════════

def main():
    if len(sys.argv) < 2:
        print("Usage: bot.py <monitor|release|ota|changelog>")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "monitor":
        monitor()
    elif cmd == "release":
        release()
    elif cmd == "ota":
        ota()
    elif cmd == "changelog":
        changelog()
    else:
        print(f"Unknown command: {cmd}")
        sys.exit(1)

if __name__ == "__main__":
    main()
