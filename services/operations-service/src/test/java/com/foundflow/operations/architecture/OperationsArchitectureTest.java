package com.foundflow.operations.architecture;

import com.foundflow.archtest.CrudServiceArchitectureSuite;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.core.importer.ImportOption;

@AnalyzeClasses(
        packages = "com.foundflow.operations",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class OperationsArchitectureTest extends CrudServiceArchitectureSuite {
}
