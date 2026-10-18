package org.jsut.modules.resource.service;

import org.jsut.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraMediaServiceTest {
    final byte[] jpeg = {(byte)255, (byte)216, 1, 2, (byte)255, (byte)217};
    final String address = "rtsp://192.0.2.10:554/live?channel=1&subtype=0";
    static class FakeMedia extends CameraMediaService {
        List<String> command;
        final Process process = mock(Process.class);
        FakeMedia(byte[] bytes) {
            super("ffmpeg-test", 1, 1);
            when(process.getInputStream()).thenAnswer(i -> new ByteArrayInputStream(bytes));
            when(process.destroyForcibly()).thenReturn(process);
        }
        @Override protected Process launch(List<String> command) { this.command = command; return process; }
    }
    @Test void rtspPreservesQueryAndEscapingWithDefaultPortAndIpv6() {
        assertEquals(address, RtspAddress.parse(address).url());
        var authenticated = RtspAddress.parse("rtsp://operator:p%40ss@camera.local/live?channel=1");
        assertEquals("rtsp://operator:p%40ss@camera.local:554/live?channel=1", authenticated.url());
        assertFalse(authenticated.toString().contains("p%40ss"));
        assertEquals("rtsp://camera.local:554/a%20b?channel=1&subtype=0", RtspAddress.parse("rtsp://camera.local/a%20b?channel=1&subtype=0").url());
        assertTrue(RtspAddress.parse("rtsp://[2001:db8::1]/live").url().endsWith(":554/live"));
        for (String invalid : List.of("http://host/live", "rtsp://host/live#x", "rtsp://999.1.1.1/live", "rtsp://host:65536/live", "rtsp://host/a b")) {
            assertThrows(BusinessException.class, () -> RtspAddress.parse(invalid));
        }
    }
    @Test void snapshotUsesTcpWithoutShellAndAlwaysReleasesProcess() {
        var media = new FakeMedia(jpeg);
        try {
            assertArrayEquals(jpeg, media.snapshot(address));
            assertTrue(media.command.contains(address));
            assertTrue(media.command.contains("-frames:v"));
            assertTrue(media.command.contains("-rtsp_transport"));
            verify(media.process).destroyForcibly();
            assertArrayEquals(jpeg, media.snapshot(address)); // slot was returned
        } finally { media.shutdown(); }
    }
    @Test void streamWritesMultipartAndRejectsExcessConnectionsUntilClosed() throws Exception {
        var media = new FakeMedia(jpeg);
        try {
            var feed = media.preview(address);
            assertEquals(429, assertThrows(BusinessException.class, () -> media.snapshot(address)).getStatus().value());
            var output = new ByteArrayOutputStream(); feed.writeTo(output);
            assertTrue(output.toString(java.nio.charset.StandardCharsets.ISO_8859_1).contains("Content-Length: 6"));
            assertArrayEquals(jpeg, media.snapshot(address));
        } finally { media.shutdown(); }
    }
    @Test void brokenFramesFailWithActionableErrorAndReleaseSlots() {
        var media = new FakeMedia(new byte[]{(byte)255, (byte)216, 1});
        try {
            assertEquals(502, assertThrows(BusinessException.class, () -> media.snapshot(address)).getStatus().value());
            assertEquals(502, assertThrows(BusinessException.class, () -> media.snapshot(address)).getStatus().value());
            verify(media.process, times(2)).destroyForcibly();
        } finally { media.shutdown(); }
    }
    @Test void missingExecutableIsReportedWithoutExposingAddress() {
        var media = new CameraMediaService("missing", 1, 1) {
            @Override protected Process launch(List<String> command) throws IOException { throw new IOException("secret process details"); }
        };
        try {
            var error = assertThrows(BusinessException.class, () -> media.snapshot(address));
            assertEquals(503, error.getStatus().value()); assertTrue(error.getMessage().contains("SOP_CAMERA_FFMPEG"));
            assertFalse(error.getMessage().contains(address));
        } finally { media.shutdown(); }
    }
    @Test void partialAndOversizedFramesAreRejected() {
        assertThrows(EOFException.class, () -> CameraMediaService.readFrame(new ByteArrayInputStream(new byte[]{(byte)255,(byte)216})));
        assertThrows(IOException.class, () -> CameraMediaService.readFrame(new ByteArrayInputStream(new byte[5*1024*1024+1])));
    }
    @Test void stalledCameraIsKilledAtDeadline() {
        var stopped = new java.util.concurrent.CountDownLatch(1);
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new InputStream() {
            @Override public int read() throws IOException {
                try { stopped.await(); return -1; } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
        });
        when(process.destroyForcibly()).thenAnswer(i -> { stopped.countDown(); return process; });
        var media = new CameraMediaService("ffmpeg", 1, 1) {
            @Override protected Process launch(List<String> command) { return process; }
        };
        try {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(4), () ->
                    assertEquals(502, assertThrows(BusinessException.class, () -> media.snapshot(address)).getStatus().value()));
            verify(process).destroyForcibly();
        } finally { media.shutdown(); }
    }
}

