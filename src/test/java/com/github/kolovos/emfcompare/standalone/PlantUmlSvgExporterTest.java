package com.github.kolovos.emfcompare.standalone;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantUmlSvgExporterTest {

	@Test
	void rendersPlantUmlSourceToSvg(@TempDir Path directory) throws Exception {
		Path source = directory.resolve("diagram.puml");
		Path svg = directory.resolve("nested/diagram.svg");
		Files.writeString(source, "@startuml\nclass Customer\n@enduml\n", StandardCharsets.UTF_8);

		new PlantUmlSvgExporter().write(source, svg);

		String rendered = Files.readString(svg, StandardCharsets.UTF_8);
		assertTrue(rendered.startsWith("<?xml") || rendered.startsWith("<svg"));
		assertTrue(rendered.contains("<svg"));
		assertTrue(rendered.contains("Customer"));
	}
}
