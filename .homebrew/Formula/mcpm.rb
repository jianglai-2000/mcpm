class Mcpm < Formula
  desc "MCP Package Manager — discover, install, and manage MCP servers"
  homepage "https://github.com/mcpm/mcpm"
  license "Apache-2.0"

  on_macos do
    on_arm do
      url "https://github.com/mcpm/mcpm/releases/latest/download/mcpm-cli-darwin-arm64.jar"
      sha256 "" # filled by CI on release
    end
    on_intel do
      url "https://github.com/mcpm/mcpm/releases/latest/download/mcpm-cli-darwin-x64.jar"
      sha256 ""
    end
  end

  on_linux do
    url "https://github.com/mcpm/mcpm/releases/latest/download/mcpm-cli-linux-x64.jar"
    sha256 ""
  end

  depends_on "openjdk@21"

  def install
    libexec.install Dir["*.jar"].first => "mcpm.jar"
    (bin/"mcpm").write <<~SHELL
      #!/bin/bash
      exec java -jar "#{libexec}/mcpm.jar" "$@"
    SHELL
  end

  test do
    assert_match "mcpm", shell_output("#{bin}/mcpm --version")
  end
end
