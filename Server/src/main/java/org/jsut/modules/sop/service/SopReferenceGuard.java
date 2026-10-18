package org.jsut.modules.sop.service;

import org.jsut.common.BusinessException;
import org.jsut.modules.sop.mapper.SopMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Called while the existing project write transaction holds its parent project lock. */
@Service
public class SopReferenceGuard {
    private final SopMapper db;
    private final org.jsut.modules.resource.mapper.ResourceMapper resources;
    public SopReferenceGuard(SopMapper db,org.jsut.modules.resource.mapper.ResourceMapper resources){this.db=db;this.resources=resources;}
    public long owned(long projectId,long userId){return db.ownedDocuments(projectId,userId);}
    public void projectDelete(long id){if(db.projectReferences(id)>0)throw conflict("项目管理了SOP或存在历史SOP关联，请保留并归档");}
    public void subprojectDelete(long id){if(db.subprojectReferences(id)>0||resources.taskReferences(id)>0)throw conflict("子项目存在SOP或任务关联记录，请保留并归档");}
    public void memberChange(long pid,long uid){if(owned(pid,uid)>0||resources.ownerTasks(pid,uid)>0)throw conflict("请先转移该成员负责的SOP或未取消的SOP任务，再移除或降级成员");}
    private BusinessException conflict(String message){return new BusinessException(HttpStatus.CONFLICT,message);}
}
