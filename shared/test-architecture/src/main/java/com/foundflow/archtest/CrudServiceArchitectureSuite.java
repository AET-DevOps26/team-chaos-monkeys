package com.foundflow.archtest;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

public abstract class CrudServiceArchitectureSuite {

    @ArchTest
    public static final ArchTests rules = ArchTests.in(StandardLayerRules.class);
}
