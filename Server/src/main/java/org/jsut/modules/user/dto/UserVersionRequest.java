package org.jsut.modules.user.dto;

import jakarta.validation.constraints.*;
public record UserVersionRequest(@NotNull @Min(0) Integer version) { }
