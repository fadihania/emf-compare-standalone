package com.github.kolovos.emfcompare.standalone;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.ComparePackage;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public class App {

	private static final Path OUTPUT_FILE = Path.of("out", "differences.txt");
	private static final Path COMPARISON_MODEL_FILE = Path.of("out", "comparison-model.xmi");
	private static final String LEFT_MODEL_URI =
		"platform:/resource/playground-example/BIMserver/models_2019-11-18_3ab7228.ecore";
	private static final String RIGHT_MODEL_URI =
		"platform:/resource/playground-example/BIMserver/models_2026-01-09_1d7577d.ecore";

	public static void main(String[] args) throws IOException {
		ComparePackage.eINSTANCE.eClass();
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());

		ResourceSet resourceSet1 = new ResourceSetImpl();
		ResourceSet resourceSet2 = new ResourceSetImpl();

		// Resource left = loadClasspathResource(resourceSet1, "/models/v1.ecore", "memory:/v1.ecore");
        // Resource right = loadClasspathResource(resourceSet2, "/models/v2.ecore", "memory:/v2.ecore");

        Resource left = loadClasspathResource(resourceSet1, "/models/BIMserver/models_2019-11-18_3ab7228.ecore",
                LEFT_MODEL_URI);
        Resource right = loadClasspathResource(resourceSet2, "/models/BIMserver/models_2026-01-09_1d7577d.ecore",
                RIGHT_MODEL_URI);

		IComparisonScope scope = new DefaultComparisonScope(left, right, null);
		Comparison comparison = EMFCompare.builder().build().compare(scope);

		List<Diff> differences = comparison.getDifferences();

		Files.createDirectories(OUTPUT_FILE.getParent());
		saveComparisonModel(comparison);
		try (PrintStream output = new PrintStream(OUTPUT_FILE.toFile(), StandardCharsets.UTF_8)) {
			for (Diff diff : differences) {
				// output.println(diff.getKind());
				output.println(diff);
			}
		}
	}

	private static Resource loadClasspathResource(ResourceSet resourceSet, String classpathLocation, String resourceUri)
		throws IOException {
		URL resourceUrl = App.class.getResource(classpathLocation);
		if (resourceUrl == null) {
			throw new IOException("Missing resource: " + classpathLocation);
		}

		Resource resource = resourceSet.createResource(URI.createURI(resourceUri));
		try (InputStream inputStream = resourceUrl.openStream()) {
			resource.load(inputStream, null);
		}
		return resource;
	}

	private static void saveComparisonModel(Comparison comparison) throws IOException {
		Files.createDirectories(COMPARISON_MODEL_FILE.getParent());

		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(ComparePackage.eNS_URI, ComparePackage.eINSTANCE);
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());

		Resource resource = resourceSet.createResource(
			URI.createFileURI(COMPARISON_MODEL_FILE.toAbsolutePath().toString()));
		resource.getContents().add(comparison);
		resource.save(Map.of(XMLResource.OPTION_ENCODING, "ASCII"));
	}

}
