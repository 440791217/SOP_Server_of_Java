package org.jsut.modules.resource.service;

import jakarta.annotation.PreDestroy;
import org.jsut.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** FFmpeg reads RTSP over TCP. No shell, temporary files, or URL logging. */
@Service
public class CameraMediaService {
    private static final int MAX_FRAME = 5 * 1024 * 1024;
    private final String executable;
    private final int timeoutSeconds;
    private final Semaphore slots;
    private final Set<Feed> feeds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r, "camera-timeouts"); thread.setDaemon(true); return thread;
    });

    public CameraMediaService(@Value("${sop.camera.ffmpeg:ffmpeg}") String executable,
                              @Value("${sop.camera.timeout-seconds:10}") int timeoutSeconds,
                              @Value("${sop.camera.max-processes:4}") int maxProcesses) {
        this.executable = executable;
        this.timeoutSeconds = Math.max(1, Math.min(30, timeoutSeconds));
        this.slots = new Semaphore(Math.max(1, maxProcesses));
    }

    protected Process launch(List<String> command) throws IOException {
        return new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    public byte[] snapshot(String address) {
        try (Feed feed = open(address, false)) { return feed.first; }
    }

    public Feed preview(String address) { return open(address, true); }

    private Feed open(String address, boolean preview) {
        String url = RtspAddress.parse(address).url();
        if (!slots.tryAcquire()) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "摄像头连接数已满，请关闭其他预览后重试");
        Feed feed = null;
        try {
            List<String> command = new ArrayList<>(List.of(executable, "-hide_banner", "-loglevel", "error", "-nostdin",
                    "-rtsp_transport", "tcp", "-rw_timeout", Long.toString(timeoutSeconds * 1000000L),
                    "-protocol_whitelist", "rtsp,tcp,udp,rtp", "-i", url, "-map", "0:v:0", "-an", "-sn", "-dn"));
            if (preview) command.addAll(List.of("-t", "40", "-vf", "fps=5"));
            else command.addAll(List.of("-frames:v", "1"));
            command.addAll(List.of("-threads", "1", "-c:v", "mjpeg", "-q:v", "4", "-f", "image2pipe", "pipe:1"));
            feed = new Feed(launch(command));
            feeds.add(feed);
            Feed active = feed;
            feed.deadline = timers.schedule(active::close, timeoutSeconds, TimeUnit.SECONDS);
            feed.first = readFrame(feed.input);
            if (feed.first == null) throw new IOException("No frame");
            feed.deadline.cancel(false);
            feed.deadline = timers.schedule(active::close, preview ? 45 : timeoutSeconds, TimeUnit.SECONDS);
            return feed;
        } catch (IOException e) {
            if (feed == null) {
                slots.release();
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "无法启动FFmpeg，请在后端配置SOP_CAMERA_FFMPEG并确认可执行");
            }
            feed.close();
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "摄像头取图失败或超时，请检查RTSP地址、访问权限及服务器网络");
        } catch (RuntimeException e) {
            if (feed == null) slots.release(); else feed.close();
            throw e;
        }
    }

    // image2pipe is a sequence of JPEGs. Bound memory even for a malformed/oversized frame.
    static byte[] readFrame(InputStream input) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        int previous = -1, count = 0, next;
        boolean started = false;
        while ((next = input.read()) != -1) {
            if (++count > MAX_FRAME) throw new IOException("Frame exceeds 5MiB");
            if (!started && previous == 0xff && next == 0xd8) {
                started = true; frame.write(0xff); frame.write(0xd8);
            } else if (started) {
                frame.write(next);
                if (previous == 0xff && next == 0xd9) return frame.toByteArray();
            }
            previous = next;
        }
        if (started) throw new EOFException("Incomplete JPEG");
        return null;
    }

    public final class Feed implements AutoCloseable {
        private final Process process;
        private final InputStream input;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile ScheduledFuture<?> deadline;
        private byte[] first;
        private Feed(Process process) { this.process = process; this.input = new BufferedInputStream(process.getInputStream()); }
        public void writeTo(OutputStream output) throws IOException {
            try (this) {
                byte[] frame = first;
                while (frame != null && !closed.get()) {
                    output.write(("--camera\r\nContent-Type: image/jpeg\r\nContent-Length: " + frame.length + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    output.write(frame); output.write('\r'); output.write('\n'); output.flush();
                    frame = readFrame(input);
                }
            }
        }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (deadline != null) deadline.cancel(false);
            process.destroyForcibly();
            try { input.close(); } catch (IOException ignored) { }
            feeds.remove(this); slots.release();
        }
    }

    @PreDestroy public void shutdown() { feeds.forEach(Feed::close); timers.shutdownNow(); }
}

