#!/bin/bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "$script_dir/../java-impetus-dependencies"

# The Central Portal endpoint is configured in the BOM; release enables GPG signing.
exec mvn clean deploy -Prelease "$@"
