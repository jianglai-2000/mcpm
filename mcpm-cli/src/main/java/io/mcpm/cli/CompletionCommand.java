package io.mcpm.cli;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * Generates shell completion scripts for bash, zsh, and fish.
 * <p>
 * Usage:
 * <pre>
 *   mcpm completion bash   # output bash completion script
 *   mcpm completion zsh    # output zsh completion script
 *   mcpm completion fish   # output fish completion script
 * </pre>
 */
@CommandLine.Command(
        name = "completion",
        description = "Generate shell completion scripts for bash, zsh, and fish.",
        mixinStandardHelpOptions = true
)
public class CompletionCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "SHELL", description = "Shell type: bash, zsh, or fish")
    String shell;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        String script = switch (shell.toLowerCase()) {
            case "bash" -> generateBash();
            case "zsh" -> generateZsh();
            case "fish" -> generateFish();
            default -> {
                System.err.println("Unknown shell: " + shell + ". Supported: bash, zsh, fish");
                System.exit(1);
                yield "";
            }
        };

        PrintWriter out = spec.commandLine().getOut();
        out.print(script);
        out.flush();
        return 0;
    }

    private String generateBash() {
        return """
# mcpm bash completion
# Source this file: source <(mcpm completion bash)
# Or install:      mcpm completion bash > /etc/bash_completion.d/mcpm

_mcpm_completions() {
    local cur prev words cword
    _init_completion || return

    # Get all commands from mcpm --help
    local commands="search info install uninstall list update detect init publish completion help"

    # If prev is mcpm (first arg), complete commands
    if [[ $cword -eq 1 ]]; then
        COMPREPLY=($(compgen -W "$commands" -- "$cur"))
        return
    fi

    # Command-specific completions
    case "${words[1]}" in
        install|update|info|uninstall)
            # Suggest package names from registry (via mcpm search)
            if [[ $cword -eq 2 ]]; then
                local pkgs
                pkgs=$(mcpm search "" --count 50 2>/dev/null | tail -n +3 | awk '{print $1}')
                COMPREPLY=($(compgen -W "$pkgs" -- "$cur"))
            fi
            ;;
        completion)
            if [[ $cword -eq 2 ]]; then
                COMPREPLY=($(compgen -W "bash zsh fish" -- "$cur"))
            fi
            ;;
        *)
            # Complete options
            COMPREPLY=($(compgen -W "-h --help -V --version" -- "$cur"))
            ;;
    esac
}

complete -F _mcpm_completions mcpm
""";
    }

    private String generateZsh() {
        return """
#compdef mcpm
# mcpm zsh completion
# Source: source <(mcpm completion zsh)

_mcpm() {
    local -a commands
    commands=(
        'search:Search for MCP server packages'
        'info:Show package details'
        'install:Install an MCP server'
        'uninstall:Remove an MCP server'
        'list:List installed servers'
        'update:Update installed servers'
        'detect:Detect configuration files'
        'init:Create a new mcp.json'
        'publish:Publish a package'
        'completion:Generate completion scripts'
        'help:Show help'
    )

    _arguments \\
        '(-h --help)'{-h,--help}'[Show help]' \\
        '(-V --version)'{-V,--version}'[Show version]' \\
        "1:command:->cmds" \\
        "*::args:->args"

    case $state in
        cmds) _describe 'command' commands ;;
        args)
            case $words[1] in
                completion) _arguments "2:shell:(bash zsh fish)" ;;
                install|update|info|uninstall) _arguments "2:package name:" ;;
            esac
        ;;
    esac
}

_mcpm "$@"
""";
    }

    private String generateFish() {
        return """
# mcpm fish completion
# Source: source <(mcpm completion fish)
# Install: mcpm completion fish > ~/.config/fish/completions/mcpm.fish

complete -c mcpm -f -a 'search'    -d 'Search for MCP server packages'
complete -c mcpm -f -a 'info'      -d 'Show package details'
complete -c mcpm -f -a 'install'   -d 'Install an MCP server'
complete -c mcpm -f -a 'uninstall' -d 'Remove an MCP server'
complete -c mcpm -f -a 'list'      -d 'List installed servers'
complete -c mcpm -f -a 'update'    -d 'Update installed servers'
complete -c mcpm -f -a 'detect'    -d 'Detect configuration files'
complete -c mcpm -f -a 'init'      -d 'Create a new mcp.json'
complete -c mcpm -f -a 'publish'   -d 'Publish a package'
complete -c mcpm -f -a 'completion' -d 'Generate completion scripts'
complete -c mcpm -f -a 'help'      -d 'Show help'

# Global options
complete -c mcpm -s h -l help    -d 'Show help'
complete -c mcpm -s V -l version -d 'Show version'

# Subcommand options
complete -c mcpm -n '__fish_seen_subcommand_from completion' -f -a 'bash zsh fish'
complete -c mcpm -n '__fish_seen_subcommand_from install' -s y -l yes -d 'Skip confirmation'
complete -c mcpm -n '__fish_seen_subcommand_from install' -s v -l version -d 'Version'
complete -c mcpm -n '__fish_seen_subcommand_from install' -s c -l config -d 'Config path'
""";
    }
}
