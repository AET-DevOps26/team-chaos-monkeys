package com.foundflow.auth.architecture;

import com.foundflow.archtest.CrudServiceArchitectureSuite;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.core.importer.ImportOption;

@AnalyzeClasses(
        packages = "com.foundflow.auth",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class AuthArchitectureTest extends CrudServiceArchitectureSuite {
}
