package org.jsut.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
public record UserStatusRequest(
        @Schema(description = "0禁用、1正常、2离职") @NotNull @Min(0) @Max(2) Integer status,
        @NotNull @Min(0) Integer version) { }
