import os
import sys
import html
import re
import contextlib
from pathlib import Path
from sys import argv

from pyrogram import Client
from pyrogram.types import InputMediaDocument

api_id = os.environ.get("APP_ID") or os.environ.get("TELEGRAM_APP_ID")
api_hash = os.environ.get("APP_HASH") or os.environ.get("TELEGRAM_APP_HASH")
artifacts_path = Path("artifacts")
test_version = argv[3] == "test" if len(argv) > 3 else False
metadata_chat_id = (
    argv[4]
    if len(argv) > 4
    and argv[4].strip()
    and argv[4].strip().lower() not in ("none", "null")
    else None
)

DEFAULT_CI_CHANNEL = "-1004471690712"
DEFAULT_MAIN_CHANNEL = "-1004473879468"

if api_id:
    with contextlib.suppress(ValueError):
        api_id = int(api_id)


def normalize_chat_id(cid):
    if not cid:
        return cid
    cid = str(cid).strip()
    if not cid.startswith("-") and not cid.startswith("@"):
        cid = f"-100{cid}"
    with contextlib.suppress(ValueError):
        cid = int(cid)
    return cid


def resolve_target_channel(target: str | None) -> tuple[int | str, str]:
    """
    Resolves the Telegram chat ID and label ("CI" or "Main").
    - Regular CI builds always go to CI channel (-1004471690712).
    - Only sends to Main channel (-1004473879468) if target is explicitly "main", "release", "prod",
      or if RELEASE_URL is set in environment (GitHub Release build).
    - Or if target is a custom chat ID or username.
    """
    target = (target or "").strip()

    main_id = os.environ.get("MAIN_CHANNEL_ID") or DEFAULT_MAIN_CHANNEL
    ci_id = os.environ.get("CI_CHANNEL_ID") or DEFAULT_CI_CHANNEL

    # Explicitly requested main / release channel or GitHub Release build:
    if target.lower() in ("main", "prod", "release", "stable") or (not target and os.environ.get("RELEASE_URL")):
        return normalize_chat_id(main_id), "Main"

    # Custom chat ID or username:
    if target and target.lower() not in ("ci", "dev", "staging", "auto", "default", "none", "null", "test"):
        normalized = normalize_chat_id(target)
        label = "Main" if str(normalized) == str(normalize_chat_id(main_id)) else "CI"
        return normalized, label

    # CI channel for all normal runs:
    return normalize_chat_id(ci_id), "CI"


def standardize_apk_name(apk: Path) -> Path:
    """
    Ensures APK filename places architecture early (e.g. NagramXFC-normal-arm64-v8a-...)
    so Telegram doesn't truncate the architecture name in chat bubbles.
    """
    name = apk.name
    flavor = "plugin" if "plugin" in str(apk).lower() else "normal"
    abi_list = ["arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal"]
    detected_abi = None
    for abi in abi_list:
        if abi in name.lower():
            detected_abi = abi
            break
    if not detected_abi:
        detected_abi = "universal"

    prefix = f"NagramXFC-{flavor}-{detected_abi}"
    if name.startswith(prefix):
        return apk

    ver_match = re.search(r"v\d+[\.\d]*(?:\(\d+\))?", name)
    ver_str = f"-{ver_match.group(0)}" if ver_match else ""

    new_name = f"{prefix}{ver_str}.apk"
    new_path = apk.parent / new_name
    if new_path != apk:
        try:
            apk.rename(new_path)
            print(f"Standardized APK name: {apk.name} -> {new_name}")
            return new_path
        except Exception as e:
            print(f"Could not rename {apk.name} to {new_name}: {e}")
            return apk
    return apk


def find_all_apks() -> list[Path]:
    if not artifacts_path.exists():
        return []
    raw_apks = list(artifacts_path.glob("**/*.apk"))
    apks = [standardize_apk_name(apk) for apk in raw_apks]
    abi_order = ["arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal"]

    def sort_key(apk: Path):
        name = apk.name.lower()
        is_plugin = "plugin" in str(apk).lower()
        matched_abi_idx = len(abi_order)
        for idx, abi in enumerate(abi_order):
            if abi in name:
                matched_abi_idx = idx
                break
        return (is_plugin, matched_abi_idx, name)

    return sorted(apks, key=sort_key)


def get_commit_info():
    commit_id_raw = os.environ.get("COMMIT_ID") or "unknown"
    commit_id = commit_id_raw[:7]
    commit_url = os.environ.get("COMMIT_URL") or "https://github.com/rhythmcache/NagramXFC/commits"
    commit_message = os.environ.get("COMMIT_MESSAGE") or "unknown"
    return commit_id, commit_url, commit_message


def format_changelog_html(text: str) -> str:
    text = (text or "").strip().replace("\\n", "\n")
    if not text:
        return ""
    has_html = bool(re.search(r"<\/?(b|i|u|s|code|pre|a|blockquote|br)\b", text, re.IGNORECASE))
    if not has_html:
        text = html.escape(text)
    return text


def split_text_message(text: str, max_length: int = 4000) -> list[str]:
    if len(text) <= max_length:
        return [text]
    chunks = []
    lines = text.split("\n")
    cur = ""
    for line in lines:
        if len(cur) + len(line) + 1 > max_length:
            if cur:
                chunks.append(cur)
            cur = line
        else:
            cur = f"{cur}\n{line}" if cur else line
    if cur:
        chunks.append(cur)
    return chunks


def get_caption(target_label: str = "CI") -> tuple[str, str | None]:
    commit_id, commit_url, commit_message = get_commit_info()
    release_url = os.environ.get("RELEASE_URL", "").strip()
    release_title = os.environ.get("RELEASE_TITLE", "").strip()
    changelog_raw = os.environ.get("CHANGELOG", "").strip()
    ai_summary = os.environ.get("AI_SUMMARY", "").strip()

    is_release = target_label == "Main" or bool(release_url)
    title_suffix = "Release Build" if is_release else "CI Staging Build"
    if release_title:
        title = f"<b>{html.escape(release_title)}</b>"
    else:
        title = f"<b>NagramXFC {title_suffix}</b>"

    header_lines = [title, ""]
    if release_url:
        header_lines.append(f"<b>GitHub Release:</b> <a href=\"{release_url}\">View on GitHub</a>")
    header_lines.append(f"<b>Commit:</b> <a href=\"{commit_url}\">{commit_id}</a>")

    footer = "\n\nChannel: @NagramXFC"
    separate_changelog = None

    changelog_content = changelog_raw or ai_summary
    if is_release and changelog_content:
        formatted_cl = format_changelog_html(changelog_content)
        cl_block = f"\n\n<b>Changelog:</b>\n<blockquote expandable>{formatted_cl}</blockquote>"
        base_header = "\n".join(header_lines)
        if len(base_header + cl_block + footer) <= 1024:
            return base_header + cl_block + footer, None
        else:
            full_msg = f"{title}\n\n<b>Changelog:</b>\n\n{formatted_cl}"
            if release_url:
                full_msg += f"\n\n<a href=\"{release_url}\"><b>GitHub Release</b></a>"
            separate_changelog = full_msg
            note = "\n\n<i>Full changelog sent below.</i>"
            if len(base_header + note + footer) <= 1024:
                return base_header + note + footer, separate_changelog
            else:
                return (base_header + footer)[:1020] + "...", separate_changelog

    escaped_msg = html.escape(commit_message.strip())
    body = f"\n<b>Commit Message:</b>\n<blockquote expandable>{escaped_msg}</blockquote>"
    if ai_summary and not is_release:
        formatted_ai = format_changelog_html(ai_summary)
        body += f"\n\n<blockquote expandable>{formatted_ai}</blockquote>"

    base_header = "\n".join(header_lines)
    total = base_header + body + footer
    if len(total) > 1024:
        total = total[:1020] + "..."
    return total, None


def get_documents(target_label: str = "CI") -> tuple[list["InputMediaDocument"], str | None]:
    documents = []
    apks = find_all_apks()
    for apk in apks:
        documents.append(
            InputMediaDocument(
                media=str(apk),
            )
        )
    if not documents:
        fallback_img = Path("TMessagesProj/src/main/ic_launcher_nagram_block_round-playstore.png")
        if fallback_img.exists():
            documents.append(InputMediaDocument(media=str(fallback_img)))
        else:
            return [], None

    caption, separate_changelog = get_caption(target_label)
    documents[0].caption = caption
    print(f"Prepared {len(documents)} document(s) for upload to {target_label}.")
    return documents, separate_changelog


def get_metadata():
    commit_id = "<code>" + (os.environ.get("COMMIT_ID") or "unknown")[:7] + "</code>"
    commit_message = "<code>" + html.escape(os.environ.get("COMMIT_MESSAGE") or "unknown") + "</code>"
    build_timestamp = "<code>" + (os.environ.get("BUILD_TIMESTAMP") or "-1") + "</code>"
    return build_timestamp + " " + commit_id + "\n" + commit_message


def retry(func):
    async def wrapper(*args, **kwargs):
        for attempt in range(1, 4):
            try:
                return await func(*args, **kwargs)
            except Exception as e:
                print(f"Attempt {attempt} failed: {e}")
                if attempt == 3:
                    raise
    return wrapper


@retry
async def send_to_channel(client: "Client", cid, target_label: str = "CI"):
    cid = normalize_chat_id(cid)
    documents, separate_changelog = get_documents(target_label)
    if not documents:
        print("No documents found to send.")
        return
    for i in range(0, len(documents), 10):
        chunk = documents[i:i + 10]
        print(f"Sending media group chunk of {len(chunk)} item(s) to {target_label} channel ({cid})...")
        await client.send_media_group(
            cid,
            media=chunk,
        )
    if separate_changelog:
        print(f"Sending separate changelog message to {target_label} channel ({cid})...")
        for chunk_text in split_text_message(separate_changelog):
            await client.send_message(
                chat_id=cid,
                text=chunk_text,
                disable_web_page_preview=True,
            )


@retry
async def send_metadata(client: "Client", cid):
    cid = normalize_chat_id(cid)
    print(f"Sending metadata message to {cid}...")
    await client.send_message(
        chat_id=cid,
        text=get_metadata(),
    )


def get_client(bot_token: str):
    return Client(
        "helper_bot",
        api_id=api_id,
        api_hash=api_hash,
        bot_token=bot_token,
    )


async def main():
    if len(argv) < 2:
        print("Usage: upload.py <bot_token> [chat_id] [version_type] [metadata_chat_id]")
        return
    bot_token = argv[1]
    raw_chat_id = argv[2] if len(argv) > 2 else None

    chat_id, target_label = resolve_target_channel(raw_chat_id)
    print(f"Resolved target channel: {chat_id} ({target_label})")

    client = get_client(bot_token)
    await client.start()
    try:
        await send_to_channel(client, chat_id, target_label)
        if metadata_chat_id:
            await send_metadata(client, metadata_chat_id)
    finally:
        await client.stop()


if __name__ == "__main__":
    from asyncio import run
    run(main())
