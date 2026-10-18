package org.jsut.modules.user.dto;

import jakarta.validation.constraints.*;
import java.util.List;
public record UserRoleRequest(@NotNull @Size(max = 100) List<@NotBlank String> role_ids,
                               @NotNull @Min(0) Integer version) { }
