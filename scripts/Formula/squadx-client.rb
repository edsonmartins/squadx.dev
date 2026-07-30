# Homebrew Formula for SquadX Client
# Install: brew tap squadx-dev/tap && brew install squadx-client

class SquadxClient < Formula
  include Language::Python::Virtualenv

  desc "AI Development Squad Orchestration Client"
  homepage "https://squadx.dev"
  url "https://github.com/squadx-dev/squadx.dev/archive/refs/tags/v0.1.0.tar.gz"
  # TODO: Update sha256 with actual release tarball hash before publishing
  sha256 "0000000000000000000000000000000000000000000000000000000000000000"
  license "MIT"

  depends_on "python@3.11"
  depends_on "git"

  # Docker is a runtime dependency, not a build dependency
  # Users must install Docker Desktop separately

  def install
    # Create virtualenv and install
    venv = virtualenv_create(libexec, "python3.11")
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
      PLACEHOLDER formula (sha256/URL not bound to a release tag).

      Supported Dev LIGHT install on macOS (ADR-0009):
        ./scripts/install-mac-client.sh
        source ~/.squadx/env.sh && squadx-client doctor

      See documentos/DEV-LIGHT-MAC.md

      Docker via Colima (preferred) or Docker Desktop is required until
      SQUADX_SANDBOX_BACKEND=process ships.
    EOS
  end

  test do
    assert_match "squadx-client", shell_output("#{bin}/squadx-client --help")
  end
end
