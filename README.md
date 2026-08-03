# EMF Compare Standalone Example

A minimal example demonstrating how to run [EMF Compare](https://eclipse.dev/emfcompare/) from a headless Java application.

Since EMF Compare is not published in a Maven repository, this repo includes a [helper script](download-emf-compare.sh) that downloads the EMF Compare JAR from its P2 repository and installs it into your local Maven repository:

```sh
./download-emf-compare.sh
```

After that, the project builds as a normal Maven project:

```sh
mvn verify
```

Running `App` writes the raw differences, serialized comparison, and PlantUML class diagram to `out/`:

- `differences.txt`
- `comparison-model.xmi`
- `comparison-model.puml`

The PlantUML source preserves EMF Compare's additions, deletions, changes, and moves. It renders
additions in green, deletions in red, and changes or moves in yellow.
