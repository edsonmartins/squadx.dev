package dev.squadx.controller.support;

import dev.squadx.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;

import jakarta.servlet.http.HttpSession;

public final class AuthenticatedUserResolver {

    private AuthenticatedUserResolver() {
    }

    public static User resolve(User user) {
        if (user != null) {
            return user;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User authenticatedUser) {
            return authenticatedUser;
        }

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
            Principal requestPrincipal = servletRequestAttributes.getRequest().getUserPrincipal();
            if (requestPrincipal instanceof Authentication requestAuthentication
                    && requestAuthentication.getPrincipal() instanceof User requestUser) {
                return requestUser;
            }

            HttpSession session = servletRequestAttributes.getRequest().getSession(false);
            if (session != null) {
                Object sessionContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
                if (sessionContext instanceof SecurityContext securityContext) {
                    Authentication sessionAuthentication = securityContext.getAuthentication();
                    if (sessionAuthentication != null && sessionAuthentication.getPrincipal() instanceof User sessionUser) {
                        return sessionUser;
                    }
                }
            }
        }

        return null;
    }

    public static User resolve(User user, HttpServletRequest request) {
        if (user != null) {
            return user;
        }

        if (request != null) {
            Principal requestPrincipal = request.getUserPrincipal();
            if (requestPrincipal instanceof Authentication requestAuthentication
                    && requestAuthentication.getPrincipal() instanceof User requestUser) {
                return requestUser;
            }

            HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessionContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
                if (sessionContext instanceof SecurityContext securityContext) {
                    Authentication sessionAuthentication = securityContext.getAuthentication();
                    if (sessionAuthentication != null && sessionAuthentication.getPrincipal() instanceof User sessionUser) {
                        return sessionUser;
                    }
                }
            }
        }

        return resolve(user);
    }
}
