package com.foundflow.archtest;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.annotation.Configuration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

public final class GatewayRules {

    private GatewayRules() {
    }

    @ArchTest
    static final ArchRule config_classes_should_be_configurations =
            classes()
                    .that().resideInAPackage("..config..")
                    .should().beAnnotatedWith(Configuration.class);
}
