package org.jsut.modules.sop.controller;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
public final class SopImageResponse {
    private SopImageResponse(){}
    public static ResponseEntity<ByteArrayResource> of(Map<String,Object> image){return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)image.get("mime"))).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename((String)image.get("name"),StandardCharsets.UTF_8).build().toString()).body(new ByteArrayResource((byte[])image.get("bytes")));}
}
