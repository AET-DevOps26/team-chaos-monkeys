package com.foundflow.founditem.architecture;

import com.foundflow.archtest.EventDrivenServiceArchitectureSuite;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;

@AnalyzeClasses(
        packages = "com.foundflow.founditem",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class FoundItemArchitectureTest extends EventDrivenServiceArchitectureSuite {
}
