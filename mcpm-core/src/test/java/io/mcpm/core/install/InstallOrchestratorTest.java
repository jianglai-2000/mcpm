package io.mcpm.core.install;

import io.mcpm.spi.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallOrchestratorTest {

    private static McpmPackage.VersionEntry versionEntry(String downloadUrl) {
        return new McpmPackage.VersionEntry("1.0", downloadUrl, null,
                Map.of("port", 8080), Map.of("JAVA_HOME", "/opt/java"));
    }

    private static McpmPackage pkg(String name, String type, McpmPackage.VersionEntry entry) {
        return new McpmPackage(name, "desc", type, "1.0",
                Map.of("1.0", entry), List.of("author"), "MIT", null, null);
    }

    // ---- install dispatch ----

    @Test
    void installDispatchesToCorrectHandler() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");
        when(handler.install(any())).thenReturn(
                PackageHandler.InstallResult.success("ok", "npx", List.of("-y", "pkg"), Map.of()));

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of());
        var pkg = pkg("@org/srv", "npx", versionEntry(null));

        var result = orchestrator.install(pkg, null, null);

        assertThat(result.success()).isTrue();
        verify(handler).install(any());
    }

    @Test
    void installRejectsUnknownType() {
        var orchestrator = new InstallOrchestrator(List.of(), List.of());
        var pkg = pkg("unknown", "unknown-type", versionEntry(null));

        var result = orchestrator.install(pkg, null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("unknown-type");
    }

    @Test
    void installRejectsMissingVersion() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of());
        var pkg = new McpmPackage("test", null, "npx", "1.0",
                Map.of(), null, null, null, null);

        var result = orchestrator.install(pkg, "2.0", null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("2.0");
    }

    @Test
    void installPassesCorrectVersion() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");
        when(handler.install(any())).thenReturn(
                PackageHandler.InstallResult.success("ok", "npx", List.of(), Map.of()));

        var v1 = versionEntry(null);
        var v2 = new McpmPackage.VersionEntry("2.0", null, null, Map.of(), Map.of());
        var pkg = new McpmPackage("test", null, "npx", "2.0",
                Map.of("1.0", v1, "2.0", v2), null, null, null, null);

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of());

        orchestrator.install(pkg, "2.0", null);

        var captor = ArgumentCaptor.forClass(PackageHandler.InstallRequest.class);
        verify(handler).install(captor.capture());
        assertThat(captor.getValue().version()).isEqualTo("2.0");
    }

    @Test
    void installUsesLatestVersionWhenNull() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");
        when(handler.install(any())).thenReturn(
                PackageHandler.InstallResult.success("ok", "npx", List.of(), Map.of()));

        var entry = versionEntry(null);
        var pkg = pkg("test", "npx", entry);

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of());

        orchestrator.install(pkg, null, null);

        var captor = ArgumentCaptor.forClass(PackageHandler.InstallRequest.class);
        verify(handler).install(captor.capture());
        assertThat(captor.getValue().version()).isEqualTo("1.0");
    }

    @Test
    void installMergesDefaultEnvWithUserEnv() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");
        when(handler.install(any())).thenReturn(
                PackageHandler.InstallResult.success("ok", "npx", List.of(), Map.of()));

        // package default: JAVA_HOME=/opt/java
        var pkg = pkg("test", "npx", versionEntry(null));

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of());
        orchestrator.install(pkg, null, Map.of("JAVA_HOME", "/custom", "MY_VAR", "hello"));

        var captor = ArgumentCaptor.forClass(PackageHandler.InstallRequest.class);
        verify(handler).install(captor.capture());

        var env = captor.getValue().env();
        assertThat(env).containsEntry("JAVA_HOME", "/custom"); // user overrides default
        assertThat(env).containsEntry("MY_VAR", "hello"); // user adds new
    }

    // ---- uninstall dispatch ----

    @Test
    void uninstallDispatchesToCorrectHandler() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");
        when(handler.uninstall(any())).thenReturn(
                PackageHandler.UninstallResult.success("ok"));

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of());
        var pkg = pkg("@org/srv", "npx", versionEntry(null));

        var result = orchestrator.uninstall(pkg, "1.0");

        assertThat(result.success()).isTrue();
        verify(handler).uninstall(any());
    }

    @Test
    void uninstallRejectsUnknownType() {
        var orchestrator = new InstallOrchestrator(List.of(), List.of());
        var pkg = pkg("unknown", "alien", versionEntry(null));

        var result = orchestrator.uninstall(pkg, "1.0");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("alien");
    }

    // ---- lifecycle hooks ----

    @Test
    void installFiresLifecycleHooksInOrder() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");
        when(handler.install(any())).thenReturn(
                PackageHandler.InstallResult.success("ok", "npx", List.of(), Map.of()));

        var hook1 = mock(LifecycleHook.class);
        var hook2 = mock(LifecycleHook.class);

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of(hook1, hook2));
        var pkg = pkg("test", "npx", versionEntry(null));

        orchestrator.install(pkg, null, null);

        // hook1 and hook2 both called before and after
        verify(hook1).beforeInstall(any(), any());
        verify(hook2).beforeInstall(any(), any());
        verify(hook1).afterInstall(any(), any());
        verify(hook2).afterInstall(any(), any());

        // before-install should be called before handler install
        var inOrder = inOrder(hook1, handler, hook2);
        inOrder.verify(hook1).beforeInstall(any(), any());
        inOrder.verify(handler).install(any());
        inOrder.verify(hook2).afterInstall(any(), any());
    }

    @Test
    void uninstallFiresLifecycleHooksInOrder() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");
        when(handler.uninstall(any())).thenReturn(
                PackageHandler.UninstallResult.success("ok"));

        var hook = mock(LifecycleHook.class);

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of(hook));
        var pkg = pkg("test", "npx", versionEntry(null));

        orchestrator.uninstall(pkg, "1.0");

        verify(hook).beforeUninstall(any(), any());
        verify(hook).afterUninstall(any(), any());

        var inOrder = inOrder(hook, handler);
        inOrder.verify(hook).beforeUninstall(any(), any());
        inOrder.verify(handler).uninstall(any());
        inOrder.verify(hook).afterUninstall(any(), any());
    }

    // ---- handler registry ----

    @Test
    void handlerForReturnsEmptyIfNotRegistered() {
        var orchestrator = new InstallOrchestrator(List.of(), List.of());

        assertThat(orchestrator.handlerFor("npx")).isEmpty();
    }

    @Test
    void handlerForReturnsRegisteredHandler() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of());

        assertThat(orchestrator.handlerFor("npx")).contains(handler);
    }

    @Test
    void handlersMapIsUnmodifiable() {
        var handler = mock(PackageHandler.class);
        when(handler.supportedType()).thenReturn("npx");

        var orchestrator = new InstallOrchestrator(List.of(handler), List.of());

        assertThatThrownBy(() -> orchestrator.handlers().put("jar", handler))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void duplicateHandlerTypeLogsWarning() {
        var h1 = mock(PackageHandler.class);
        var h2 = mock(PackageHandler.class);
        when(h1.supportedType()).thenReturn("npx");
        when(h2.supportedType()).thenReturn("npx");

        // should not throw — log warning instead
        var orchestrator = new InstallOrchestrator(List.of(h1, h2), List.of());

        // later registration wins (h2)
        assertThat(orchestrator.handlerFor("npx")).contains(h2);
    }
}
