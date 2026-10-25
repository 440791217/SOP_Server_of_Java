package org.jsut.modules.sop.mapper;

import org.apache.ibatis.annotations.*;
import java.util.*;

@Mapper
public interface SopMapper {
    Map<String,Object> detection(@Param("stepId") long stepId);
    int clearDetection(@Param("stepId") long stepId);
    int saveDetection(Map<String,Object> data);
    Map<String,Object> detectionCamera(@Param("id") long id);
    long wrongCameraStation(@Param("sopId") long sopId,@Param("stationId") long stationId);
    List<Map<String,Object>> list(Map<String,Object> q);
    long count(Map<String,Object> q);
    List<Map<String,Object>> projectOptions(Map<String,Object> q);
    Map<String,Object> document(@Param("id") long id);
    Map<String,Object> lockDocument(@Param("id") long id);
    List<Map<String,Object>> revisions(@Param("sopId") long sopId);
    Map<String,Object> revision(@Param("sopId") long sopId,@Param("id") long id);
    List<Map<String,Object>> steps(@Param("revisionId") long revisionId);
    Map<String,Object> step(@Param("revisionId") long revisionId,@Param("id") long id);
    List<Map<String,Object>> attachments(@Param("stepId") long stepId);
    List<Map<String,Object>> rawAttachments(@Param("stepId") long stepId);
    long nextRevision(@Param("sopId") long sopId);
    long drafts(@Param("sopId") long sopId);
    long published(@Param("sopId") long sopId);
    long bindings(@Param("sopId") long sopId);
    long activeBindings(@Param("revisionId") long revisionId);
    long projectReferences(@Param("projectId") long projectId);
    long subprojectReferences(@Param("subprojectId") long subprojectId);
    long ownedDocuments(@Param("projectId") long projectId,@Param("userId") long userId);
    int insertDocument(Map<String,Object> data);
    int insertRevision(Map<String,Object> data);
    int insertStep(Map<String,Object> data);
    int copyAttachment(Map<String,Object> data);
    int editDocument(Map<String,Object> data);
    int owner(Map<String,Object> data);
    int bumpDocument(Map<String,Object> data);
    int editRevision(Map<String,Object> data);
    int bumpRevision(Map<String,Object> data);
    int editStep(Map<String,Object> data);
    int deleteStep(@Param("revisionId") long revisionId,@Param("id") long id,@Param("version") int version);
    int deleteAttachments(@Param("stepId") long stepId);
    int sortStep(@Param("revisionId") long revisionId,@Param("id") long id,@Param("position") int position);
    int publish(Map<String,Object> data);
    int retire(Map<String,Object> data);
    int discard(Map<String,Object> data);
    int discardAll(Map<String,Object> data);
    int deleteDocument(Map<String,Object> data);
    int insertFile(Map<String,Object> data);
    Map<String,Object> file(@Param("id") long id);
    Map<String,Object> revisionFile(@Param("revisionId") long revisionId,@Param("fileId") long fileId);
    int deleteAttachment(@Param("stepId") long stepId,@Param("id") long id);
    List<Map<String,Object>> publishedOptions(Map<String,Object> q);
}
