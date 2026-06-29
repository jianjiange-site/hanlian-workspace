package com.aurora.dating.user.service;

import com.aurora.dating.user.entity.UserInfoEntity;

public record ResolveOrCreateResult(UserInfoEntity user, boolean created) {
}
