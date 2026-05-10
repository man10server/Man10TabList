{
  description = "Man10TabList - Velocity plugin development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        jdk = pkgs.jdk21;
        gradle = pkgs.gradle.override { java = jdk; };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            gradle
          ];

          shellHook = ''
            export JAVA_HOME="${jdk.home}"
            export GRADLE_USER_HOME="$PWD/.gradle"

            echo "=== Man10TabList dev shell ==="
            echo "  JDK    : $(java -version 2>&1 | head -n1)"
            echo "  Gradle : $(gradle -v 2>/dev/null | awk '/^Gradle/ {print $2}')"
            echo "=============================="
          '';
        };

        formatter = pkgs.nixpkgs-fmt;
      });
}
