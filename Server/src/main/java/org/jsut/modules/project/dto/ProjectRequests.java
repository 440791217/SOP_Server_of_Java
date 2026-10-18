package org.jsut.modules.project.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class ProjectRequests {
    private ProjectRequests() {}
    public record Create(@NotBlank @Size(max=64) @Pattern(regexp="PRJ-[0-9]{12}-[0-9A-F]{6}",message="项目编号格式不正确") String code,@NotBlank @Size(max=128) String name,
                         @Size(max=128) String customer_name, @NotNull @Positive Long owner_user_id,
                         @Size(max=5000) String description) {}
    public record Edit(@NotBlank @Size(max=128) String name, @Size(max=128) String customer_name,
                       @Size(max=5000) String description, @NotNull @Min(0) Integer version) {}
    public record SubCreate(@NotBlank @Size(max=64) @Pattern(regexp="SUB-[0-9]{12}-[0-9A-F]{6}",message="子项目编号格式不正确") String code,@NotBlank @Size(max=128) String name,
                            @NotNull @Positive Long owner_user_id, @Size(max=5000) String description) {}
    public record SubEdit(@NotBlank @Size(max=128) String name, @Positive Long owner_user_id,
                          @Size(max=5000) String description, @NotNull @Min(0) Integer version) {}
    public record MemberAdd(@NotNull @Positive Long user_id, @NotNull @Min(0) @Max(1) Integer access_level,
                            @NotNull @Min(0) Integer version) {}
    public record MemberEdit(@NotNull @Min(0) @Max(1) Integer access_level, @NotNull @Min(0) Integer version) {}
    public record Owner(@NotNull @Positive Long owner_user_id, @NotNull @Min(0) Integer version) {}
    public record Status(@NotNull @Min(0) @Max(3) Integer status, @NotNull @Min(0) Integer version) {}
    public record Version(@NotNull @Min(0) Integer version) {}
    public record Sort(@NotNull @Size(max=10000) List<@NotNull @Positive Long> ids,
                       @NotNull @Min(0) Integer version) {}
}
