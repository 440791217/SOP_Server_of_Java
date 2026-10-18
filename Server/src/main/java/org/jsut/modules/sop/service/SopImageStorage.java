package org.jsut.modules.sop.service;

import org.jsut.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Private immutable image objects. Never map this directory as static resources. */
@Service
public class SopImageStorage {
    public static final long MAX_BYTES=5L*1024*1024;
    private final Path root;
    public SopImageStorage(@Value("${sop.storage.directory:./data/sop-files}") String directory){root=Path.of(directory).toAbsolutePath().normalize();}
    public record Stored(String key,String name,String mime,byte[] bytes,byte[] sha) {}
    public Stored save(MultipartFile file){
        if(file==null||file.isEmpty())throw bad("请选择图片");if(file.getSize()>MAX_BYTES)throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,"图片不能超过5MiB");
        try{
            byte[] bytes;try(var stream=file.getInputStream()){bytes=stream.readNBytes((int)MAX_BYTES+1);}if(bytes.length>MAX_BYTES)throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,"图片不能超过5MiB");
            String format=validate(bytes);String key=UUID.randomUUID().toString().replace("-","")+(format.equals("png")?".png":".jpg");
            String name=file.getOriginalFilename();name=name==null?key:name.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");if(name.isBlank())name=key;if(name.length()>255)name=name.substring(0,255);
            Files.createDirectories(root);Files.write(path(key),bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            return new Stored(key,name,format.equals("png")?"image/png":"image/jpeg",bytes,digest(bytes));
        }catch(IOException e){throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"图片保存失败，请重试");}
    }
    private String validate(byte[] bytes)throws IOException{
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw bad("仅支持有效的PNG或JPEG图片");var reader=readers.next();
            try{reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);if(!format.equals("png")&&!format.equals("jpeg")&&!format.equals("jpg"))throw bad("仅支持PNG或JPEG图片");
                int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>8192||h>8192||(long)w*h>16_000_000)throw bad("图片尺寸过大，最大8192像素边长及1600万像素");if(reader.read(0)==null)throw bad("图片内容损坏");return format.equals("png")?"png":"jpeg";
            }catch(IOException e){throw bad("图片内容损坏");}finally{reader.dispose();}
        }
    }
    public byte[] read(Map<String,Object> file){
        try{Path p=path((String)file.get("storage_key"));if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw missing();long length=Files.size(p);if(length<1||length>MAX_BYTES||length!=((Number)file.get("byte_size")).longValue())throw missing();byte[] bytes=Files.readAllBytes(p);if(!MessageDigest.isEqual(digest(bytes),(byte[])file.get("sha256")))throw missing();return bytes;}
        catch(IOException e){throw missing();}
    }
    public void validateStored(Map<String,Object> file){if(file==null||((Number)file.get("file_kind")).intValue()!=0)throw bad("图片不存在或不受支持");read(file);}
    public void rollbackDelete(String key){try{Files.deleteIfExists(path(key));}catch(IOException ignored){/* Orphan can be reconciled against sop_file; never delete referenced objects. */}}
    private Path path(String key){if(key==null||!key.matches("[0-9a-f]{32}\\.(png|jpg)"))throw missing();Path p=root.resolve(key).normalize();if(!p.startsWith(root))throw missing();return p;}
    private byte[] digest(byte[] bytes){try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private BusinessException bad(String m){return new BusinessException(HttpStatus.BAD_REQUEST,m);}
    private BusinessException missing(){return new BusinessException(HttpStatus.NOT_FOUND,"图片不存在或存储校验失败");}
}
