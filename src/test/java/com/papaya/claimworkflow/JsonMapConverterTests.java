package com.papaya.claimworkflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.papaya.claimworkflow.entity.IdempotencyRequest;
import com.papaya.claimworkflow.entity.JsonMapConverter;
import java.math.BigDecimal;
import java.util.Map;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;

class JsonMapConverterTests {
    @Test
    void jsonRoundTripPreservesPersistedWorkflowNumberTypes() {
        var converter = new JsonMapConverter();
        Map<String, Object> response = Map.of("version", 3L, "facts",
                Map.of("amount", new BigDecimal("500.00"), "policyLimit", new BigDecimal("1000.00")));
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(response))).isEqualTo(response);
    }

    @Test
    void hibernateSnapshotDoesNotMakeAnUnchangedResponseDirty() {
        try (var factory = new Configuration()
                .addAnnotatedClass(IdempotencyRequest.class)
                .addAttributeConverter(JsonMapConverter.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .buildSessionFactory()) {
            var sessionFactory = factory.unwrap(SessionFactoryImplementor.class);
            var persister = sessionFactory.getMappingMetamodel().getEntityDescriptor(IdempotencyRequest.class);
            assertThat(persister.isMutable()).as("Idempotency outcomes must never issue UPDATE statements").isFalse();
            var type = persister.getPropertyType("responseBody");
            Map<String, Object> response = Map.of("version", 3L, "status", "COMMITTED");
            Object snapshot = type.deepCopy(response, sessionFactory);
            assertThat(type.isEqual(response, snapshot)).isTrue();
        }
    }
}
