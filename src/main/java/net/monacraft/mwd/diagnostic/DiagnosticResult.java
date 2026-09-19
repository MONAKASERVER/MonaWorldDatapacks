package net.monacraft.mwd.diagnostic;
public record DiagnosticResult(Severity severity, String check, String detail) {
    public enum Severity { OK, WARNING, ERROR }
}

