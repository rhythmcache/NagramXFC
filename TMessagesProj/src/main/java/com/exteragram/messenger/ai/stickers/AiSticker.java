package com.exteragram.messenger.ai.stickers;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

public class AiSticker {

    public long documentId;
    public String emoji;
    public String description;
    public boolean isAnimated;
    public boolean isVideo;
    public boolean isGif;
    public String serializedDocumentHex;
    public long dateAdded;
    public long originDialogId;
    public int originMessageId;

    public AiSticker() {}

    public AiSticker(long documentId, String emoji, String description, boolean isAnimated, boolean isVideo, boolean isGif, String serializedDocumentHex) {
        this(documentId, emoji, description, isAnimated, isVideo, isGif, serializedDocumentHex, 0, 0);
    }

    public AiSticker(long documentId, String emoji, String description, boolean isAnimated, boolean isVideo, boolean isGif, String serializedDocumentHex, long originDialogId, int originMessageId) {
        this.documentId = documentId;
        this.emoji = emoji;
        this.description = description;
        this.isAnimated = isAnimated;
        this.isVideo = isVideo;
        this.isGif = isGif;
        this.serializedDocumentHex = serializedDocumentHex;
        this.originDialogId = originDialogId;
        this.originMessageId = originMessageId;
        this.dateAdded = System.currentTimeMillis();
    }

    public TLRPC.Document getDocument() {
        if (serializedDocumentHex == null || serializedDocumentHex.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Utilities.hexToBytes(serializedDocumentHex);
            if (bytes == null || bytes.length == 0) return null;
            SerializedData data = new SerializedData(bytes);
            TLRPC.Document doc = TLRPC.Document.TLdeserialize(data, data.readInt32(false), false);
            data.cleanup();
            return doc;
        } catch (Exception e) {
            FileLog.e("AiSticker deserialize error", e);
            return null;
        }
    }

    public static String serializeDocument(TLRPC.Document document) {
        if (document == null) return null;
        try {
            SerializedData data = new SerializedData(document.getObjectSize());
            document.serializeToStream(data);
            byte[] bytes = data.toByteArray();
            data.cleanup();
            return Utilities.bytesToHex(bytes);
        } catch (Exception e) {
            FileLog.e("AiSticker serialize error", e);
            return null;
        }
    }
}
