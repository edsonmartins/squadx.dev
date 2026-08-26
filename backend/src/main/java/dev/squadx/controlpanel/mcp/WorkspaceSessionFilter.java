package dev.squadx.controlpanel.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica chamadas às tools do workspace ({@code /api/v1/workspace/tools/**}) via token de sessão
 * MCP, populando o contexto com uma {@link WorkspaceAuthentication}. Demais rotas passam adiante
 * (o {@code JwtAuthenticationFilter} de usuário cuida delas).
 *
 * <p>{@code @ConditionalOnBean}: o {@code WorkspaceSessionProvider} é um {@code @Component} que o
 * slice {@code @WebMvcTest} não varre. Sem o bean, este filtro não é criado — mantém o context de
 * teste dos controllers carregável. Em runtime o provider existe e o filtro é registrado no chain.</p>
 */
@Component
@ConditionalOnBean(WorkspaceSessionProvider.class)
@RequiredArgsConstructor
public class WorkspaceSessionFilter extends OncePerRequestFilter {

    private static final String TOOLS_PATH = "/api/v1/workspace/tools";

    private final WorkspaceSessionProvider sessionProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String authHeader = request.getHeader("Authorization");

        if (path != null && path.startsWith(TOOLS_PATH)
                && SecurityContextHolder.getContext().getAuthentication() == null
                && authHeader != null && authHeader.startsWith("Bearer ")) {
            WorkspaceSession session = sessionProvider.parse(authHeader.substring(7));
            if (session != null) {
                WorkspaceAuthentication auth = new WorkspaceAuthentication(session);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}

