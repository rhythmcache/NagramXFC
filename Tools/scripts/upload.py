import os
import sys
import html
import contextlib
from pathlib import Path
from sys import argv

from pyrogram import Client
from pyrogram.types import InputMediaDocument

api_id = os.environ.get("APP_ID") or os.environ.get("TELEGRAM_APP_ID")
api_hash = os.environ.get("APP_HASH") or os.environ.get("TELEGRAM_APP_HASH")
artifacts_path = Path("artifacts")
test_version = argv[3] == "test" if len(argv) > 3 else False
metadata_chat_id = argv[4] if len(argv) > 4 and argv[4].strip() and argv[4].strip().lower() not in ("none", "null") else None

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


def find_all_apks() -> list[Path]:
    if not artifacts_path.exists():
        return []
    apks = list(artifacts_path.glob("**/*.apk"))
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


def get_caption() -> str:
    commit_id, commit_url, commit_message = get_commit_info()
    title = "<b>NagramXFC Staging Build</b>" if test_version else "<b>NagramXFC Release Build</b>"
    escaped_msg = html.escape(commit_message.strip())
    caption = (
        f"{title}\n\n"
        f"<b>Commit:</b> <a href=\"{commit_url}\">{commit_id}</a>\n"
        f"<b>Commit Message:</b>\n<blockquote expandable>{escaped_msg}</blockquote>\n\n"
        f"Channel: @NagramXFC"
    )
    return caption


def get_ai_summary():
    ai_summary = os.environ.get("AI_SUMMARY", "")
    if ai_summary:
        return "\n\n<blockquote expandable>" + normalize_message(ai_summary) + "</blockquote>"
    return ""


def normalize_message(text: str) -> str:
    return (text or "").replace("\\n", "\n")


def get_documents() -> list["InputMediaDocument"]:
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
            return []

    base_caption = get_caption()
    ai_summary = get_ai_summary()
    total_caption = base_caption
    if ai_summary and len(total_caption + ai_summary) <= 1024:
        total_caption += ai_summary
    elif len(total_caption) > 1024:
        total_caption = total_caption[:1020] + "..."

    documents[0].caption = total_caption
    print(f"Prepared {len(documents)} APK document(s) for upload.")
    return documents


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
async def send_to_channel(client: "Client", cid):
    cid = normalize_chat_id(cid)
    documents = get_documents()
    if not documents:
        print("No documents found to send.")
        return
    for i in range(0, len(documents), 10):
        chunk = documents[i:i + 10]
        print(f"Sending media group chunk of {len(chunk)} item(s) to {cid}...")
        await client.send_media_group(
            cid,
            media=chunk,
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
    if len(argv) < 3:
        print("Usage: upload.py <bot_token> <chat_id> [version_type] [metadata_chat_id]")
        return
    bot_token = argv[1]
    chat_id = argv[2]
    client = get_client(bot_token)
    await client.start()
    try:
        await send_to_channel(client, chat_id)
        if metadata_chat_id:
            await send_metadata(client, metadata_chat_id)
    finally:
        await client.stop()


if __name__ == "__main__":
    from asyncio import run
    run(main())
