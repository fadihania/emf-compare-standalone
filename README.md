# EMFCompare Standalone Example
A minimal example demonstrating how to run EMFCompare from a headless Java application.

The project now uses Tycho to resolve OSGi dependencies from the EMF Compare P2 repository instead of relying on the Eclipse IDE target platform.

## Build

Run:

```sh
mvn verify
```

Tycho resolves dependencies from:

- `https://download.eclipse.org/modeling/emf/emf/builds/release`
- `https://download.eclipse.org/modeling/emf/compare/updates/releases/3.3`
- `https://download.eclipse.org/tools/orbit/simrel/maven-osgi/release`

## Run in Eclipse

Import the repository as an existing Maven project, then right click on `App.java` in the `Package Explorer` view and select `Run As -> Java Application`.
