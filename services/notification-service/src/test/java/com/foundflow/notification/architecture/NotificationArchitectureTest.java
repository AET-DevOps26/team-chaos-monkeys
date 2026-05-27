package com.foundflow.notification.architecture;

import com.foundflow.archtest.CrudServiceArchitectureSuite;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.core.importer.ImportOption;

@AnalyzeClasses(
        packages = "com.foundflow.notification",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class NotificationArchitectureTest extends CrudServiceArchitectureSuite {
}
