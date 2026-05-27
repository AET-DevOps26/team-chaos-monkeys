package com.foundflow.archtest;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

public abstract class GatewayArchitectureSuite {

    @ArchTest
    public static final ArchTests rules = ArchTests.in(GatewayRules.class);
}
