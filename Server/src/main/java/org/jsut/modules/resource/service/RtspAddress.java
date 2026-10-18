package org.jsut.modules.resource.service;

import java.net.URI;
import static org.jsut.modules.resource.service.ResourceSupport.bad;

/** Keeps escaped credentials, query parameters and paths intact. */
public record RtspAddress(String host, int port, String path, String userInfo) {
    public static RtspAddress parse(String value) {
        try {
            if (value == null || value.length() > 1300 || value.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException();
            URI uri = URI.create(value);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 554 : uri.getPort();
            if (!"rtsp".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank()
                    || uri.getRawFragment() != null || port < 1 || port > 65535) throw new IllegalArgumentException();
            if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
            if (host.contains(":") || host.matches("[0-9.]+")) host = StationIp.normalize(host);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            if (host.length() > 255 || path.length() > 1000) throw new IllegalArgumentException();
            return new RtspAddress(host, port, path, uri.getRawUserInfo());
        } catch (RuntimeException e) {
            throw bad("请填写有效的完整RTSP地址，特殊字符需进行URL编码，不包含#片段");
        }
    }

    public String url() {
        return "rtsp://" + (userInfo == null ? "" : userInfo + "@") + (host.contains(":") ? "[" + host + "]" : host) + ":" + port + path;
    }
    public String displayUrl() { return new RtspAddress(host, port, path, null).url(); }
    @Override public String toString() { return displayUrl(); }
}
