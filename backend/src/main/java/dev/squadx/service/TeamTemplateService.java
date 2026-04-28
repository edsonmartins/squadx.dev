package dev.squadx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.template.ApplyTemplateRequest;
import dev.squadx.dto.template.TemplateResponse;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.Organization;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.model.enums.AgentType;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.SquadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamTemplateService {

    private final ObjectMapper objectMapper;
    private final SquadRepository squadRepository;
    private final AgentRepository agentRepository;
    private final OrganizationRepository organizationRepository;

    public List<TemplateResponse> listTemplates() {
        List<TemplateResponse> templates = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:templates/*.json");
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    TemplateResponse template = objectMapper.readValue(is, TemplateResponse.class);
                    templates.add(template);
                }
            }
        } catch (IOException e) {
            log.error("Failed to load templates", e);
        }
        return templates;
    }

    public TemplateResponse getTemplate(String name) {
        try (InputStream is = openTemplateStream(name)) {
            return objectMapper.readValue(is, TemplateResponse.class);
        } catch (IOException e) {
            throw new ResourceNotFoundException("Template not found: " + name);
        }
    }

    private InputStream openTemplateStream(String name) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream classpathStream = classLoader.getResourceAsStream("templates/" + name + ".json");
        if (classpathStream != null) {
            return classpathStream;
        }

        Optional<InputStream> resolverStream = openTemplateStreamFromResolver(name);
        if (resolverStream.isPresent()) {
            return resolverStream.get();
        }

        for (Path fallbackPath : fallbackTemplatePaths(name)) {
            if (Files.exists(fallbackPath)) {
                return Files.newInputStream(fallbackPath);
            }
        }

        throw new ResourceNotFoundException("Template not found: " + name);
    }

    private Optional<InputStream> openTemplateStreamFromResolver(String name) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:templates/*.json");
        String expectedFilename = name + ".json";

        for (Resource resource : resources) {
            if (expectedFilename.equals(resource.getFilename())) {
                return Optional.of(resource.getInputStream());
            }
        }

        return Optional.empty();
    }

    private List<Path> fallbackTemplatePaths(String name) {
        String templateFile = name + ".json";
        String userDir = System.getProperty("user.dir");

        return List.of(
                Path.of("src/main/resources/templates", templateFile),
                Path.of("backend/src/main/resources/templates", templateFile),
                Path.of(userDir, "src/main/resources/templates", templateFile),
                Path.of(userDir, "backend/src/main/resources/templates", templateFile)
        );
    }

    @Transactional
    public Squad applyTemplate(String name, ApplyTemplateRequest request, User user) {
        TemplateResponse template = getTemplate(name);

        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        String squadName = request.getSquadName() != null
                ? request.getSquadName()
                : template.getName() + " Squad";

        String description = substituteVariables(template.getDescription(), request);

        Squad squad = Squad.builder()
                .name(squadName)
                .description(description)
                .organization(org)
                .build();

        squad = squadRepository.save(squad);

        for (TemplateResponse.TemplateAgent templateAgent : template.getAgents()) {
            String agentDescription = substituteVariables(templateAgent.getDescription(), request);
            Agent agent = Agent.builder()
                    .name(templateAgent.getName())
                    .agentType(AgentType.valueOf(templateAgent.getType()))
                    .description(agentDescription)
                    .squad(squad)
                    .build();
            agentRepository.save(agent);
        }

        return squad;
    }

    private String substituteVariables(String text, ApplyTemplateRequest request) {
        if (text == null) {
            return null;
        }
        String result = text;
        if (request.getSquadName() != null) {
            result = result.replace("{project_name}", request.getSquadName());
        }
        if (request.getGoal() != null) {
            result = result.replace("{goal}", request.getGoal());
        }
        return result;
    }
}
