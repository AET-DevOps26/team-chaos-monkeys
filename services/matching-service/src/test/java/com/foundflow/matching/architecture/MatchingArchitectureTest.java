package com.foundflow.matching.architecture;

import com.foundflow.archtest.EventDrivenServiceArchitectureSuite;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.core.importer.ImportOption;

@AnalyzeClasses(
        packages = "com.foundflow.matching",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class MatchingArchitectureTest extends EventDrivenServiceArchitectureSuite {
}
