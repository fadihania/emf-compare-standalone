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