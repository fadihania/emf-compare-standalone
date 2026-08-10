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

Running `App` writes the raw differences, serialized comparison, PlantUML class diagram, and matched
elements CSV to `out/<folder>/`. Output names use the model name, left version, right version, and a
collision counter. Differences include `diff` and matching CSV files include `matchings`, for example:

`out/osate-osate2/aadl2__2022-09-29_d31992b__2011-04-25_6be208d__1.xmi`

`out/osate-osate2/aadl2__2022-09-29_d31992b__2011-04-25_6be208d__diff__1.txt`

- `differences.txt`
- `comparison-model.xmi`
- `comparison-model.puml`
- `matching-elements.csv`

The PlantUML source preserves EMF Compare's additions, deletions, changes, and moves. It renders
additions in green, deletions in red, and changes or moves in yellow.
