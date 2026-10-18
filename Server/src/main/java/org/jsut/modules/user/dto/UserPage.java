package org.jsut.modules.user.dto;

import java.util.List;
public record UserPage(List<UserManagementView> items, long total) { }
