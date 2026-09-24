package com.orderflow.common.security;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the current authenticated principal from the security context. */
public final class AuthFacade {

    private AuthFacade() {}

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }

    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }
}
