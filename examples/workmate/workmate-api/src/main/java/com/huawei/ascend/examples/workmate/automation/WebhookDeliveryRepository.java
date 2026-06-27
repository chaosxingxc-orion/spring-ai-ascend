package com.huawei.ascend.examples.workmate.automation;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, java.util.UUID> {

    List<WebhookDelivery> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
