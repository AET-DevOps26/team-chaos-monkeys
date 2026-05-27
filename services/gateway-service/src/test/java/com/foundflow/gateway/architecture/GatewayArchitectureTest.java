package com.foundflow.gateway.architecture;

import com.foundflow.archtest.GatewayArchitectureSuite;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.core.importer.ImportOption;

@AnalyzeClasses(
        packages = "com.foundflow.gateway",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class GatewayArchitectureTest extends GatewayArchitectureSuite {
}
