package dev.squadx.repository;

import dev.squadx.model.CalendarIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarIntegrationRepository extends JpaRepository<CalendarIntegration, Long> {

    Optional<CalendarIntegration> findByUserIdAndOrganizationIdAndProvider(Long userId, Long organizationId, String provider);

    Optional<CalendarIntegration> findByUserIdAndProvider(Long userId, String provider);

    List<CalendarIntegration> findByUserIdAndEnabled(Long userId, Boolean enabled);

    List<CalendarIntegration> findByOrganizationId(Long organizationId);

    void deleteByUserIdAndOrganizationIdAndProvider(Long userId, Long organizationId, String provider);
}
