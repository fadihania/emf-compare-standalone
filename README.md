# EMFCompare Standalone Example

A minimal example demonstrating how to run EMF Compare from a headless Java application.

## First-time setup

EMF Compare is not published in a normal Maven repository. The repository includes a helper script that downloads the EMF Compare JAR from Eclipse and installs it into your local Maven repository:

```sh
./download-emf-compare.sh
```

After that, the project builds as a normal Maven project:

```sh
mvn verify
```