package org.jsut.modules.resource.service;
import org.jsut.common.BusinessException;
import org.jsut.modules.user.dto.UserInfo;
import org.springframework.http.HttpStatus;
import java.util.*;
public final class ResourceSupport {
 private ResourceSupport(){}
 public static BusinessException bad(String s){return new BusinessException(HttpStatus.BAD_REQUEST,s);}
 public static BusinessException conflict(String s){return new BusinessException(HttpStatus.CONFLICT,s);}
 public static BusinessException denied(){return new BusinessException(HttpStatus.FORBIDDEN,"无此资源或任务的操作权限");}
 public static BusinessException missing(){return new BusinessException(HttpStatus.NOT_FOUND,"资源或任务不存在");}
 public static long n(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}
 public static long uid(UserInfo a){return Long.parseLong(a.id());}
 public static boolean has(UserInfo a,String p){return a.is_superuser()||a.permissions().contains(p);}
 public static void version(Map<String,Object> m,Integer v){if(v==null||n(m,"version")!=v)throw conflict("数据已更新，请刷新后重试");}
 public static void changed(int n){if(n!=1)throw conflict("数据已更新，请刷新后重试");}
 public static Map<String,Object> q(String kind,long id){var q=new HashMap<String,Object>();q.put("kind",kind);q.put("id",id);return q;}
 public static Map<String,Object> view(Map<String,Object> row){var v=new HashMap<String,Object>();row.forEach((k,x)->{if(k.equals("deleted_at"))return;if(x!=null&&(k.equals("id")||k.endsWith("_id")||k.endsWith("_by")))x=x.toString();v.put(k,x);});return v;}
}
