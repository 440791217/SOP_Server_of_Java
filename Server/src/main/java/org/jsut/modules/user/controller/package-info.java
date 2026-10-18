/**
 * 用户、角色和班组接口统一放在本包，不继续拆分子模块。
 * 用户接口以 /user 开头，认证使用 /user/auth。
 * 当前实现登录、登出、当前用户查询和本人改密；管理接口后续实现权限校验再开放。
 */
package org.jsut.modules.user.controller;
