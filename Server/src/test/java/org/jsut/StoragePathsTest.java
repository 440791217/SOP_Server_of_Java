package org.jsut;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;
import org.jsut.common.storage.StoragePaths;
import org.jsut.modules.sop.service.SopImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class StoragePathsTest {
 @TempDir Path testRoot;
 @Test void relativeImagesRemainReadableAfterMovingRootAndLegacyRemainsSupported()throws Exception{
  Path root=testRoot.resolve("source"),old=testRoot.resolve("legacy");var storage=new SopImageStorage(root.toString(),old.toString());
  var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
  var file=new MockMultipartFile("file","test.png","image/png",bytes.toByteArray());var paths=new StoragePaths();
  String folder=paths.annotation(3,4L,5L,10,2);assertEquals("projects/project-3/subproject-4/task-5/annotations/v002",folder);
  var saved=storage.save(file,folder,"step-22_");var row=new HashMap<String,Object>();row.put("storage_key",saved.key());row.put("byte_size",saved.bytes().length);row.put("sha256",saved.sha());
  assertArrayEquals(bytes.toByteArray(),storage.read(row));assertFalse(saved.key().contains(":"));
  Path destination=testRoot.resolve("destination");Files.createDirectories(destination.resolve(saved.key()).getParent());Files.copy(root.resolve(saved.key()),destination.resolve(saved.key()));
  assertArrayEquals(bytes.toByteArray(),new SopImageStorage(destination.toString(),old.toString()).read(row));
  var legacy=storage.save(file);row.put("storage_key",legacy.key());assertArrayEquals(bytes.toByteArray(),storage.read(row));
  row.put("storage_key","projects/../../escape.png");assertThrows(RuntimeException.class,()->storage.read(row));
 }
 @Test void productSegmentsStaySafeAndRepeatExecutionsHaveIndependentDirectories(){
  var paths=new StoragePaths();String folder=paths.execution(3,4,5,LocalDate.of(2026,10,23),"../../产品:001",1001);
  assertTrue(folder.startsWith("projects/project-3/subproject-4/task-5/executions/2026-10-23/"));assertFalse(folder.contains(".."));assertFalse(folder.contains(":"));assertTrue(folder.endsWith("_exec-1001"));
  assertNotEquals(folder,paths.execution(3,4,5,LocalDate.of(2026,10,23),"../../产品:001",1002));
 }
}
