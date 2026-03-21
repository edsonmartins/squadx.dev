# Homebrew Formula for SquadX Client
# Install: brew tap squadx-dev/tap && brew install squadx-client

class SquadxClient < Formula
  include Language::Python::Virtualenv

  desc "AI Development Squad Orchestration Client"
  homepage "https://squadx.dev"
  url "https://github.com/squadx-dev/squadx.dev/archive/refs/tags/v0.1.0.tar.gz"
  sha256 "UPDATE_WITH_ACTUAL_SHA256"
  license "MIT"

  depends_on "python@3.12"
  depends_on "git"

  # Docker is a runtime dependency, not a build dependency
  # Users must install Docker Desktop separately

  def install
    # Create virtualenv and install
    venv = virtualenv_create(libexec, "python3.12")
    venv.pip_install_and_link buildpath/"client"

    # Install wrapper script
    bin.install_symlink libexec/"bin/squadx-client"
  end

  def post_install
    # Create default data directory
    (var/"squadx").mkpath
  end

  def caveats
    <<~EOS
      SquadX Client has been installed!

      Before using, configure your API keys:
        export OPENAI_API_KEY=sk-...
        # or
        export ANTHROPIC_API_KEY=sk-ant-...

      Docker is required for agent sandboxing:
        brew install --cask docker

      Get started:
        squadx-client --help
        https://docs.squadx.dev
    EOS
  end

  test do
    assert_match "squadx-client", shell_output("#{bin}/squadx-client --help")
  end
end
