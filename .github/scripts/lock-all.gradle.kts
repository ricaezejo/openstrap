// CI only (OSV-Scanner step): locks every configuration so `dependencies --write-locks` writes gradle.lockfile files
// for the scanner to read. Nothing is committed, so updating a dependency never needs a relock.
allprojects {
    dependencyLocking { lockAllConfigurations() }
}
