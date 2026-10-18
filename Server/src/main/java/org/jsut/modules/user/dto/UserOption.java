package org.jsut.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record UserOption(@Schema(description = "ID字符串") String id, String name, String code, Integer status) { }
