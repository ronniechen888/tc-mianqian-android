package online.thundercloud.pay.util;

import android.net.Uri;
import android.text.TextUtils;

public final class ServerConfigUtils {

    private ServerConfigUtils() {
    }

    public static ParsedConfig parse(String configText) {
        if (TextUtils.isEmpty(configText)) return null;
        String value = configText.trim();

        String[] whitespaceSplit = value.split("\\s+");
        if (whitespaceSplit.length == 2) {
            return fromParts(whitespaceSplit[0], whitespaceSplit[1]);
        }

        if (looksLikeApiBaseOnly(value)) return null;

        int splitIndex = value.lastIndexOf('/');
        if (splitIndex <= 0 || splitIndex >= value.length() - 1) return null;
        return fromParts(value.substring(0, splitIndex), value.substring(splitIndex + 1));
    }

    private static ParsedConfig fromParts(String basePart, String secretPart) {
        String baseUrl = normalizeBaseUrl(basePart);
        String secret = safeTrim(secretPart);
        if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(secret)) return null;
        return new ParsedConfig(baseUrl, secret);
    }

    public static String normalizeBaseUrl(String value) {
        String input = safeTrim(value);
        if (TextUtils.isEmpty(input)) return "";

        String withScheme = input.startsWith("http://") || input.startsWith("https://")
                ? input
                : "http://" + input;
        Uri uri = Uri.parse(withScheme);
        String scheme = safeTrim(uri.getScheme());
        String authority = safeTrim(uri.getAuthority());
        if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(authority)) return "";

        String path = safeTrim(uri.getEncodedPath());
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return scheme + "://" + authority + path;
    }

    public static String buildApiUrl(String baseUrl, String pathAndQuery) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        if (TextUtils.isEmpty(normalizedBase)) return "";
        if (TextUtils.isEmpty(pathAndQuery)) return normalizedBase;
        return pathAndQuery.startsWith("/") ? normalizedBase + pathAndQuery : normalizedBase + "/" + pathAndQuery;
    }

    public static String getAdminUrl(String baseUrl) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        if (TextUtils.isEmpty(normalizedBase)) return "";
        Uri uri = Uri.parse(normalizedBase);
        String scheme = safeTrim(uri.getScheme());
        String authority = safeTrim(uri.getAuthority());
        if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(authority)) return "";
        return scheme + "://" + authority;
    }

    public static String toDisplayText(String baseUrl, String secret) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        String trimmedSecret = safeTrim(secret);
        if (TextUtils.isEmpty(normalizedBase)) return trimmedSecret;
        if (TextUtils.isEmpty(trimmedSecret)) return normalizedBase;
        return normalizedBase + "/" + trimmedSecret;
    }

    public static boolean looksLikeApiBaseOnly(String value) {
        String normalized = normalizeBaseUrl(value);
        if (TextUtils.isEmpty(normalized)) return false;
        Uri uri = Uri.parse(normalized);
        String path = safeTrim(uri.getPath());
        if (TextUtils.isEmpty(path)) return false;
        String[] segments = path.replaceFirst("^/+", "").split("/");
        return segments.length == 3 && "api".equals(segments[0]) && "v1".equals(segments[1]);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class ParsedConfig {
        public final String baseUrl;
        public final String secret;

        public ParsedConfig(String baseUrl, String secret) {
            this.baseUrl = baseUrl;
            this.secret = secret;
        }
    }
}
