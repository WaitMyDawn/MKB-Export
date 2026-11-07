package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;

/**
 * 文件布局不参与状态身份；键排序保留可复现性，缩进仅用于持久化载荷。
 */
public final class ReadableJson {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private ReadableJson() {
    }

    public static byte[] bytes(JsonElement value) {
        return (JSON.toJson(JsonParser.parseString(IconState.canonical(value))) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
