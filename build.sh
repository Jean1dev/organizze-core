#!/usr/bin/env bash
set -e

echo "Building Clojure application..."

# Create target directory
mkdir -p target/uberjar

# Build uberjar using depstar
clojure -M:uberjar

echo "Build completed successfully!"
