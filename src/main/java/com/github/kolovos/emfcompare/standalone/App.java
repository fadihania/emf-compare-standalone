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

	private static final Path OUTPUT_DIRECTORY = Path.of("out");
	private static final boolean INCLUDE_EXTENSION_RELATIONSHIPS = false;
	private static final boolean GENERATE_PER_CLASS_CHANGE_DIAGRAMS = false;

	public static void main(String[] args) throws IOException {
		ComparePackage.eINSTANCE.eClass();
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());

		ResourceSet resourceSet1 = new ResourceSetImpl();
		ResourceSet resourceSet2 = new ResourceSetImpl();

		// Resource right = loadClasspathResource(resourceSet1, "/models/library-example/model_v1.ecore", "memory:/v1.ecore");
        // Resource left = loadClasspathResource(resourceSet2, "/models/library-example/model_v2.ecore", "memory:/v2.ecore");

        // BIMserver
        // String rootPath = "/models/BIMserver/";
        // Resource right = loadClasspathResource(resourceSet1, rootPath + "models_2011-03-10_53f28be.ecore",
        //         "platform:/resource" + rootPath + "models_2011-03-10_53f28be.ecore");
        // Resource right = loadClasspathResource(resourceSet1, rootPath + "models_2019-11-18_3ab7228.ecore",
        //         "platform:/resource" + rootPath + "models_2019-11-18_3ab7228.ecore");
        // Resource left = loadClasspathResource(resourceSet2, rootPath + "models_2026-01-09_1d7577d.ecore",
        //         "platform:/resource" + rootPath + "models_2026-01-09_1d7577d.ecore");

        // SysML-v2-Pilot-Implementation
        // String rootPath = "/models/SysML-v2-Pilot-Implementation/";
        // Resource right = loadClasspathResource(resourceSet1, rootPath + "SysML_2018-06-01_fe76e5a.ecore",
        //         "platform:/resource" + rootPath + "SysML_2018-06-01_fe76e5a.ecore");
        // Resource left = loadClasspathResource(resourceSet2, rootPath + "SysML_2026-05-27_ffd0ab3.ecore",
        //         "platform:/resource" + rootPath + "SysML_2026-05-27_ffd0ab3.ecore");

        // eclipse-platform-eclipse.platform.ui
        // String rootPath = "/models/eclipse-platform-eclipse.platform.ui/";
        // Resource right = loadClasspathResource(resourceSet1, rootPath + "UIElements_2009-10-14_4f980b6.ecore",
        //         "platform:/resource" + rootPath + "UIElements_2009-10-14_4f980b6.ecore");
        // Resource left = loadClasspathResource(resourceSet2, rootPath + "UIElements_2025-10-15_c521dfc.ecore",
        //         "platform:/resource" + rootPath + "UIElements_2025-10-15_c521dfc.ecore");

        // osate-osate2
        // String rootPath = "/models/osate-osate2/";
        // Resource right = loadClasspathResource(resourceSet1, rootPath + "aadl2_2011-04-25_6be208d.ecore",
        //         "platform:/resource" + rootPath + "aadl2_2011-04-25_6be208d.ecore");
        // Resource left = loadClasspathResource(resourceSet2, rootPath + "aadl2_2022-09-29_d31992b.ecore",
        //         "platform:/resource" + rootPath + "aadl2_2022-09-29_d31992b.ecore");

        // Semantic Model Comparison
        String rootPath = "/models/semantic-model-comparison/";
        Resource right = loadClasspathResource(resourceSet1, rootPath + "thesis-system1.ecore",
                "platform:/resource" + rootPath + "thesis-system1.ecore");
        Resource left = loadClasspathResource(resourceSet2, rootPath + "thesis-system2.ecore",
                "platform:/resource" + rootPath + "thesis-system2.ecore");

		IComparisonScope scope = new DefaultComparisonScope(left, right, null);
		Comparison comparison = EMFCompare.builder().build().compare(scope);

		List<Diff> differences = comparison.getDifferences();
		ComparisonOutputNamer.OutputPaths outputPaths =
			ComparisonOutputNamer.create(left, right, OUTPUT_DIRECTORY);

		new PlantUmlComparisonExporter().write(
			comparison, outputPaths.plantUml(), INCLUDE_EXTENSION_RELATIONSHIPS);
		new PlantUmlSvgExporter().write(outputPaths.plantUml(), outputPaths.svg());
		if (GENERATE_PER_CLASS_CHANGE_DIAGRAMS) {
			new PlantUmlChangeDiagramExporter().write(comparison, outputPaths.plantUml().getParent());
		}
		new MatchedElementsCsvExporter().write(comparison, outputPaths.matchingElements());
		saveComparisonModel(comparison, outputPaths.comparisonModel());
		try (PrintStream output = new PrintStream(outputPaths.differences().toFile(), StandardCharsets.UTF_8)) {
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

	private static void saveComparisonModel(Comparison comparison, Path output) throws IOException {
		Files.createDirectories(output.toAbsolutePath().getParent());

		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(ComparePackage.eNS_URI, ComparePackage.eINSTANCE);
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());

		Resource resource = resourceSet.createResource(
			URI.createFileURI(output.toAbsolutePath().toString()));
		resource.getContents().add(comparison);
		resource.save(Map.of(XMLResource.OPTION_ENCODING, "ASCII"));
	}

}
