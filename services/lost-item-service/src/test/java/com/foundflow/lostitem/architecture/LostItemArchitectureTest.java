package com.foundflow.lostitem.architecture;

import com.foundflow.archtest.EventDrivenServiceArchitectureSuite;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.core.importer.ImportOption;

@AnalyzeClasses(
        packages = "com.foundflow.lostitem",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class LostItemArchitectureTest extends EventDrivenServiceArchitectureSuite {
}
