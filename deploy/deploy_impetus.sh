#!/bin/bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "$script_dir/.."

# The Central Portal endpoint is configured in the BOM; native-image is not published.
exec mvn clean deploy -Prelease -pl '!java-impetus-native-image' -am "$@"
