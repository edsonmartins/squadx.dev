package dev.squadx.config;

import dev.squadx.model.User;
import dev.squadx.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    @AfterReturning("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    public void auditPost(JoinPoint joinPoint) {
        audit(joinPoint, "CREATE");
    }

    @AfterReturning("@annotation(org.springframework.web.bind.annotation.PutMapping)")
    public void auditPut(JoinPoint joinPoint) {
        audit(joinPoint, "UPDATE");
    }

    @AfterReturning("@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    public void auditPatch(JoinPoint joinPoint) {
        audit(joinPoint, "UPDATE");
    }

    @AfterReturning("@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void auditDelete(JoinPoint joinPoint) {
        audit(joinPoint, "DELETE");
    }

    private void audit(JoinPoint joinPoint, String operation) {
        try {
            User user = extractUser();
            String ipAddress = extractIpAddress();
            String resourceType = extractResourceType(joinPoint);
            Long resourceId = extractResourceId(joinPoint);
            String methodName = joinPoint.getSignature().getName();

            String action = operation + "_" + resourceType.toUpperCase();

            auditService.log(user, action, resourceType, resourceId, null, ipAddress);
        } catch (Exception e) {
            log.warn("Failed to create audit log for {}.{}: {}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    e.getMessage());
        }
    }

    private User extractUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private String extractIpAddress() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractResourceType(JoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        // Remove "Controller" suffix to get resource type
        return className.replace("Controller", "").toUpperCase();
    }

    private Long extractResourceId(JoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }
}
