package org.jsut.modules.user.service;

import org.jsut.common.BusinessException;
import org.jsut.modules.user.dto.*;
import org.jsut.modules.user.entity.ManagedUser;
import org.jsut.modules.user.mapper.AuthMapper;
import org.jsut.modules.user.mapper.UserManagementMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.*;
import java.util.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class UserManagementService {
    private final UserManagementMapper mapper;
    private final AuthMapper authMapper;
    private final AuthService auth;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public UserManagementService(UserManagementMapper mapper, AuthMapper authMapper, AuthService auth,
                                 PasswordEncoder encoder, Clock clock) {
        this.mapper = mapper;
        this.authMapper = authMapper;
        this.auth = auth;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public UserPage list(String token, int page, int limit, String username, String realName,
                         String cardNumber, String teamId, Integer status) {
        var actor = permitted(token, "user:list");
        if (page < 1 || limit < 1 || limit > 100 || (status != null && (status < 0 || status > 2))) {
            throw bad("分页或状态参数不正确");
        }
        Map<String, Object> q = new HashMap<>();
        q.put("username", optional(username,64)); q.put("realName",optional(realName,64));
        q.put("cardNumber",optional(cardNumber,64)); q.put("teamId", optionalId(teamId));
        q.put("status",status); q.put("limit",limit); q.put("offset", (long)(page-1)*limit);
        var items = mapper.list(q).stream().map(row -> view(row, actor)).toList();
        return new UserPage(items, mapper.count(q));
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public UserManagementView detail(String token, long id) {
        var actor = permitted(token,"user:list");
        return view(required(id),actor);
    }

    public UserManagementView create(String token, UserCreateRequest request) {
        var actor = writeActor(token, null, "user:create");
        String username = requiredText(request.username(),64).toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9_.-]{3,64}") || username.equals("root")) {
            throw bad("账号须为3至64位字母、数字、点、下划线或连字符，root为保留账号");
        }
        Map<String,Object> data = profile(request.real_name(),request.card_number(),request.team_id(),
                request.phone(),request.avatar_url(),null);
        var roles = request.role_ids() == null ? null : roleIds(request.role_ids());
        if (roles != null) checkRoles(actor,roles);
        else {
            Long member = mapper.memberRole();
            if (member == null) throw bad("普通用户角色未配置或已禁用，请检查初始角色数据");
            roles = List.of(member);
        }
        checkPassword(request.password());
        if (request.status() == null || request.status() < 0 || request.status() > 2) throw bad("用户状态不正确");
        data.put("username",username); data.put("passwordHash",encoder.encode(request.password()));
        data.put("status",request.status()); mapper.insert(data);
        long id = ((Number)data.get("id")).longValue();
        replaceRoles(id,roles);
        return view(required(id),actor);
    }

    public UserManagementView update(String token, long id, UserUpdateRequest request) {
        var actor = writeActor(token,id,"user:update");
        var target = writable(id,request.version());
        Map<String,Object> data = profile(request.real_name(),request.card_number(),request.team_id(),
                request.phone(),request.avatar_url(),target.teamId());
        List<Long> roles = null;
        if (request.role_ids() != null) {
            roles = roleIds(request.role_ids());
            var previous = mapper.userRoles(id).stream().map(r -> Long.valueOf(r.id())).toList();
            if (new HashSet<>(previous).equals(new HashSet<>(roles))) roles = null;
            else {
                if (actor.id().equals(Long.toString(id))) throw forbidden("不能修改自己的角色");
                checkRoles(actor,roles,new HashSet<>(previous));
            }
        }
        data.put("id",id); data.put("version",request.version());
        changed(mapper.update(data));
        if (roles != null) replaceRoles(id,roles);
        return view(required(id),actor);
    }

    public void status(String token, long id, UserStatusRequest request) {
        var actor = writeActor(token,id,"user:status");
        writable(id,request.version());
        if (actor.id().equals(Long.toString(id))) throw forbidden("不能修改自己的账号状态");
        if (request.status() == null || request.status() < 0 || request.status() > 2) throw bad("用户状态不正确");
        var data = mutation(id,request.version()); data.put("status",request.status());
        changed(mapper.status(data));
        if (request.status() != 1) authMapper.revokeAllSessions(id,now());
    }

    public void delete(String token, long id, UserVersionRequest request) {
        var actor = writeActor(token,id,"user:delete");
        writable(id,request.version());
        if (actor.id().equals(Long.toString(id))) throw forbidden("不能删除自己");
        changed(mapper.softDelete(mutation(id,request.version())));
        authMapper.revokeAllSessions(id,now());
    }

    public void resetPassword(String token, long id, UserPasswordResetRequest request) {
        var actor = writeActor(token,id,"user:reset-password");
        writable(id,request.version());
        if (actor.id().equals(Long.toString(id))) throw forbidden("请通过本人修改密码接口改密");
        checkPassword(request.new_password());
        var data = mutation(id,request.version()); data.put("passwordHash",encoder.encode(request.new_password()));
        changed(mapper.resetPassword(data)); authMapper.revokeAllSessions(id,now());
    }

    public void roles(String token, long id, UserRoleRequest request) {
        var actor = writeActor(token,id,"user:assign-role");
        writable(id,request.version());
        if (actor.id().equals(Long.toString(id))) throw forbidden("不能修改自己的角色");
        var roles = roleIds(request.role_ids());
        var existing = mapper.userRoles(id).stream().map(role -> Long.valueOf(role.id())).toList();
        checkRoles(actor,roles,new HashSet<>(existing));
        changed(mapper.bumpVersion(mutation(id,request.version()))); replaceRoles(id,roles);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<UserOption> teams(String token) {
        var actor = auth.authenticate(token);
        any(actor,"user:list","user:create","user:update");
        return mapper.teamOptions();
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<UserOption> roles(String token) {
        var actor = auth.authenticate(token);
        any(actor,"user:create","user:assign-role");
        return mapper.roleOptions().stream().filter(r -> actor.is_superuser() || actor.roles().contains(r.code())).toList();
    }

    private UserInfo writeActor(String token, Long target, String permission) {
        var initial = permitted(token,permission);
        long actorId = Long.parseLong(initial.id());
        if (target != null && target <= 0) throw bad("用户ID不正确");
        if (target != null && target < actorId) authMapper.lockById(target);
        authMapper.lockById(actorId);
        if (target != null && target > actorId) authMapper.lockById(target);
        auth.requireCurrentSession(token,actorId);
        var actor = permitted(token,permission);
        if (target != null && !actor.is_superuser() && target != actorId) {
            // 重置高级账号密码同样可以造成提权，不能只限制角色分配接口。
            boolean foreignRole = mapper.userRoles(target).stream()
                    .anyMatch(role -> !role.code().equals("member") && !actor.roles().contains(role.code()));
            boolean foreignPermission = authMapper.permissions(target).stream()
                    .anyMatch(code -> !actor.permissions().contains(code));
            if (foreignRole || foreignPermission) throw forbidden("不能管理超出本人授权范围的账号");
        }
        return actor;
    }

    private UserInfo permitted(String token, String permission) {
        var actor = auth.authenticate(token);
        if (actor.must_change_password()) throw forbidden("请先修改初始密码");
        if (!has(actor,permission)) throw forbidden("没有此操作权限");
        return actor;
    }

    private ManagedUser writable(long id,Integer version) {
        var target = required(id);
        if (Boolean.TRUE.equals(target.isSuperuser())) throw forbidden("不能通过用户管理修改、禁用或删除root");
        if (version == null || !version.equals(target.version())) throw conflict();
        return target;
    }

    private ManagedUser required(long id) {
        if (id <= 0) throw bad("用户ID不正确");
        var row = mapper.detail(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND,"用户不存在或已删除");
        return row;
    }

    private UserManagementView view(ManagedUser row,UserInfo actor) {
        var roles = mapper.userRoles(row.id());
        return new UserManagementView(row.id().toString(),row.username(),row.realName(),row.cardNumber(),
                row.teamId() == null ? null : row.teamId().toString(),row.teamName(),row.phone(),row.avatarUrl(),
                row.status(),Boolean.TRUE.equals(row.isSuperuser()),utc(row.createdAt()),utc(row.updatedAt()),
                row.version(),roles,has(actor,"user:assign-role") ? roles.stream().map(UserOption::id).toList() : null);
    }

    private Map<String,Object> profile(String realName,String card,String team,String phone,String avatar,Long oldTeam) {
        Map<String,Object> data = new HashMap<>();
        Long teamId = optionalId(team);
        if (teamId != null) {
            var option = mapper.team(teamId);
            if (option == null || (!Objects.equals(oldTeam,teamId) && option.status() != 1)) throw bad("所选班组不存在或已禁用");
        }
        String url = optional(avatar,255);
        if (url != null) {
            try {
                var parsed = URI.create(url);
                boolean relative = url.startsWith("/") && !url.startsWith("//");
                boolean absolute = ("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme())) && parsed.getHost() != null;
                if (!relative && !absolute) throw bad("头像须为HTTP(S)地址或以单个/开头的站内路径");
            } catch (IllegalArgumentException exception) { throw bad("头像地址格式不正确"); }
        }
        data.put("realName",requiredText(realName,64)); data.put("cardNumber",optional(card,64));
        data.put("teamId",teamId); data.put("phone",optional(phone,20)); data.put("avatarUrl",url); data.put("now",now());
        return data;
    }

    private void checkRoles(UserInfo actor,List<Long> roles) {
        checkRoles(actor,roles,Set.of());
    }

    private void checkRoles(UserInfo actor,List<Long> roles,Set<Long> existing) {
        if (!has(actor,"user:assign-role")) throw forbidden("没有分配角色权限");
        for(long id:roles) {
            if (existing.contains(id)) continue;
            var role = mapper.role(id);
            if (role == null || role.status() != 1) throw bad("角色不存在或已禁用");
            if (!actor.is_superuser() && !actor.roles().contains(role.code())) throw forbidden("不能授予本人未持有的角色");
        }
    }

    private List<Long> roleIds(List<String> values) {
        if (values == null || values.size()>100) throw bad("角色参数不正确");
        var result = values.stream().map(this::id).toList();
        if (new HashSet<>(result).size()!=result.size()) throw bad("角色不能重复");
        return result;
    }

    private void replaceRoles(long id,List<Long> roles) {
        mapper.removeRoles(id);
        for(long roleId:roles) mapper.addRole(id,roleId);
    }

    private long id(String value) {
        try { if(value == null || !value.matches("[0-9]{1,19}")) throw bad("ID参数不正确");
            long result=Long.parseLong(value); if(result<=0)throw bad("ID参数不正确"); return result;
        } catch(NumberFormatException exception){throw bad("ID参数不正确");}
    }
    private Long optionalId(String value) { return value==null||value.isBlank()?null:id(value.trim()); }
    private String optional(String value,int max) {
        if(value==null||value.isBlank())return null; String result=value.trim();
        if(result.length()>max)throw bad("字段长度超出限制");return result;
    }
    private String requiredText(String value,int max) { var result=optional(value,max);if(result==null)throw bad("必填字段不能为空");return result; }
    private void checkPassword(String value) { if(value==null||value.isBlank()||value.length()<8||value.length()>128)throw bad("密码长度为8至128个字符"); }
    private boolean has(UserInfo actor,String permission) { return actor.is_superuser()||actor.permissions().contains(permission); }
    private void any(UserInfo actor,String...permissions) { if(actor.must_change_password()||Arrays.stream(permissions).noneMatch(p->has(actor,p)))throw forbidden("没有此操作权限"); }
    private Map<String,Object> mutation(long id,int version) { var data=new HashMap<String,Object>();data.put("id",id);data.put("version",version);data.put("now",now());return data; }
    private void changed(int count) { if(count!=1)throw conflict(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private String utc(LocalDateTime value) { return value==null?null:value.toInstant(ZoneOffset.UTC).toString(); }
    private static BusinessException bad(String message) { return new BusinessException(HttpStatus.BAD_REQUEST,message); }
    private static BusinessException forbidden(String message) { return new BusinessException(HttpStatus.FORBIDDEN,message); }
    private static BusinessException conflict() { return new BusinessException(HttpStatus.CONFLICT,"数据已被修改，请刷新后重试"); }
}
