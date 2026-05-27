package com.foundflow.archtest;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public final class MessagingLayerRules {

    private MessagingLayerRules() {
    }

    @ArchTest
    static final ArchRule controllers_should_be_rest_controllers =
            classes()
                    .that().resideInAPackage("..controller..")
                    .should().beAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule controllers_should_not_depend_on_repositories_or_messaging =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..repository..", "..messaging..");

    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses()
                    .that().resideInAPackage("..service..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule repositories_should_not_depend_on_higher_layers =
            noClasses()
                    .that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..controller..", "..service..", "..messaging..");
}
