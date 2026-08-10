package com.github.kolovos.emfcompare.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComparisonOutputNamerTest {

	@Test
	void namesDateVersionedModelsInLeftThenRightOrder(@TempDir Path output) throws Exception {
		Resource left = resource("platform:/resource/models/osate-osate2/aadl2_2022-09-29_d31992b.ecore");
		Resource right = resource("platform:/resource/models/osate-osate2/aadl2_2011-04-25_6be208d.ecore");

		ComparisonOutputNamer.OutputPaths paths = ComparisonOutputNamer.create(left, right, output);

		assertEquals("osate-osate2", paths.comparisonModel().getParent().getFileName().toString());
		assertEquals("aadl2__2022-09-29_d31992b__2011-04-25_6be208d__1.xmi",
			paths.comparisonModel().getFileName().toString());
		assertEquals("aadl2__2022-09-29_d31992b__2011-04-25_6be208d__1.puml",
			paths.plantUml().getFileName().toString());
		assertEquals("aadl2__2022-09-29_d31992b__2011-04-25_6be208d__diff__1.txt",
			paths.differences().getFileName().toString());
		assertEquals("aadl2__2022-09-29_d31992b__2011-04-25_6be208d__matchings__1.csv",
			paths.matchingElements().getFileName().toString());
	}

	@Test
	void usesModelFallbackForStandaloneVersionsAndIncrementsCounter(@TempDir Path output) throws Exception {
		Resource left = resource("platform:/resource/models/library-example/v2.ecore");
		Resource right = resource("platform:/resource/models/library-example/v1.ecore");
		Path folder = output.resolve("library-example");
		Files.createDirectories(folder);
		Files.createFile(folder.resolve("model__v2__v1__1.xmi"));
		Files.createFile(folder.resolve("model__v2__v1__diff__3.txt"));

		ComparisonOutputNamer.OutputPaths paths = ComparisonOutputNamer.create(left, right, output);

		assertEquals("library-example", paths.comparisonModel().getParent().getFileName().toString());
		assertEquals("model__v2__v1__4.xmi",
			paths.comparisonModel().getFileName().toString());
		assertEquals("model__v2__v1__matchings__4.csv",
			paths.matchingElements().getFileName().toString());
	}

	@Test
	void parsesNumericSuffixVersions(@TempDir Path output) throws Exception {
		Resource left = resource("platform:/resource/models/library-example/thesis-system2.ecore");
		Resource right = resource("platform:/resource/models/library-example/thesis-system1.ecore");

		ComparisonOutputNamer.OutputPaths paths = ComparisonOutputNamer.create(left, right, output);

		assertEquals("thesis-system__2__1__1.xmi",
			paths.comparisonModel().getFileName().toString());
	}

	private static Resource resource(String uri) {
		return new ResourceImpl(URI.createURI(uri));
	}
}
