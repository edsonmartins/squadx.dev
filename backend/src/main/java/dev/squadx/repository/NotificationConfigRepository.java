package dev.squadx.repository;

import dev.squadx.model.NotificationChannel;
import dev.squadx.model.NotificationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfig, Long> {

    List<NotificationConfig> findByOrganizationIdAndEnabledTrue(Long organizationId);

    List<NotificationConfig> findByOrganizationId(Long organizationId);

    Optional<NotificationConfig> findByOrganizationIdAndChannel(Long organizationId, NotificationChannel channel);

    boolean existsByOrganizationIdAndChannel(Long organizationId, NotificationChannel channel);

    void deleteByOrganizationIdAndChannel(Long organizationId, NotificationChannel channel);
}
